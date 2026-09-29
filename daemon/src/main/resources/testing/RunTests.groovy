import jetbrains.mps.baseLanguage.unitTest.platform.*
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.tool.environment.Environment
import jetbrains.mps.testbench.junit.runners.PushEnvironmentRunnerBuilder
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.runner.Request
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.notification.RunListener

// MPS 2024/2025 expose a singleton; 2026 publishes a platform component.
def oldPlatform = TestPlatform.methods.find { it.name == 'getInstance' }
def tests = oldPlatform ? oldPlatform.invoke(null) : environment.platform.findComponent(TestPlatform)
assert tests != null: 'MPS testing platform is unavailable'
def participant = tests.aggregateDiscoveryParticipant
def discover = { node -> participant.discover(node, new TestDiscoveryRequest(new TestDescriptor())).orElse(null) }
def cases = []
def records = []
project.modelAccess.runReadAction {
    if (selection instanceof SNode) {
        def root = selection
        while (root.parent != null) root = root.parent
        def descriptor = discover(root)
        def flatten
        flatten = { d -> d == null ? [] : [d] + d.tests.collectMany { flatten(it) } }
        def descriptors = flatten(descriptor)
        def selected = descriptors.find { it.source instanceof SNodeTestSource && it.source.nodeReference == selection.reference }
        if (selected == null) {
            def ancestor = selection.parent
            def nearest = null
            while (ancestor != null && nearest == null) {
                nearest = descriptors.find { it.source instanceof SNodeTestSource && it.source.nodeReference == ancestor.reference }
                ancestor = ancestor.parent
            }
            throw new IllegalArgumentException("Selected node is not a runnable test. Select ${nearest?.source?.nodeReference ?: selection.model.reference}, its module, or the project.")
        }
        if (!selected.isContainer()) cases << [descriptor: selected.getContainer(), method: selected.shortName]
        else cases << [descriptor: selected, method: null]
    } else {
        def models = selection instanceof SModel ? [selection] :
            (selection instanceof SModule ? [selection] : project.projectModulesWithGenerators).collectMany { it.models.toList() }
        models.findAll { !jetbrains.mps.smodel.SModelStereotype.isStubModel(it) }.each { model ->
            model.rootNodes.each { root ->
                def descriptor = discover(root)
                if (descriptor != null) cases << [descriptor: descriptor, method: null]
            }
        }
    }
    def manager = environment.platform.findComponent(ClassLoaderManager)
    cases.each { selected ->
        def descriptor = selected.descriptor
        def module = descriptor.source.moduleReference.resolve(project.repository)
        def klass
        try { klass = manager.getClassLoader(module).loadOwnClass(descriptor.fullName) }
        catch (Throwable failure) { throw new IllegalStateException("Cannot load ${descriptor.fullName} (${descriptor.source.nodeReference})", failure) }
        records << [klass: klass, method: selected.method, descriptor: descriptor,
                    legacy: descriptor.getProperty(TestProperties.USE_COMPATIBILITY_MODE)]
    }
}
if (records.empty) throw new IllegalStateException('No tests discovered for the selection')

def sourceFor = { className, methodName ->
    def record = records.find { it.klass.name == className }
    def descriptor = record?.descriptor
    def method = descriptor?.tests?.find { it.shortName == methodName }
    (method ?: descriptor)?.source?.nodeReference?.toString()
}
def emitJUnit = { id, status, detail ->
    def source = id.source.orElse(null)
    def className = source?.metaClass?.respondsTo(source, 'getClassName') ? source.className : null
    def methodName = source?.metaClass?.respondsTo(source, 'getMethodName') ? source.methodName : null
    emit.accept([id:id.uniqueId, name:id.displayName, kind:id.isTest() ? 'TEST' : 'CONTAINER', status:status,
                 className:className, methodName:methodName, source:sourceFor(className, methodName), detail:detail])
}
def listener = [
    executionStarted: { id -> emitJUnit(id, 'RUNNING', null) },
    executionSkipped: { id, reason -> emitJUnit(id, 'SKIPPED', reason) },
    executionFinished: { id, result -> emitJUnit(id, result.status.toString() == 'SUCCESSFUL' ? 'PASSED' : result.status.toString(),
        result.throwable.map { t -> def w = new StringWriter(); t.printStackTrace(new PrintWriter(w)); w.toString() }.orElse(null)) },
    dynamicTestRegistered: { id -> },
    testPlanExecutionStarted: { plan -> }, testPlanExecutionFinished: { plan -> }, reportingEntryPublished: { id, entry -> }
] as TestExecutionListener

def config = new TestSessionConfig().withAccessory(Environment, environment)
    .withSystemProperty(SystemProperties.PROJECT_PATH, projectDirectory)
def mpsSession = tests.openSession(config)
def context = Thread.currentThread().contextClassLoader
Thread.currentThread().contextClassLoader = TestExecutionListener.classLoader
try {
    def launcherConfig = LauncherConfig.builder().enableTestEngineAutoRegistration(true)
        .enablePostDiscoveryFilterAutoRegistration(false).enableLauncherSessionListenerAutoRegistration(false)
        .enableLauncherDiscoveryListenerAutoRegistration(false).enableTestExecutionListenerAutoRegistration(false).build()
    def session = LauncherFactory.openSession(launcherConfig)
    try {
        if (!oldPlatform) {
            def namespace = session.class.classLoader.loadClass('org.junit.platform.engine.support.store.Namespace')
            session.store.put(namespace.getMethod('create', Object[].class).invoke(null, [['MPS'] as Object[]] as Object[]), 'TestSession', mpsSession)
        }
        def selectors = records.findAll { !it.legacy }.collect { r ->
            if (r.method == null) return DiscoverySelectors.selectClass(r.klass)
            def methods = []
            for (def type = r.klass; type != null; type = type.superclass) {
                methods.addAll(type.declaredMethods.findAll { it.name == r.method && !it.synthetic })
            }
            methods = methods.unique { it.parameterTypes.toList() }
            if (methods.size() != 1) throw new IllegalStateException("Cannot uniquely select ${r.klass.name}.${r.method} (${r.descriptor.source.nodeReference})")
            DiscoverySelectors.selectMethod(r.klass, methods.first())
        }
        def plan = selectors.empty ? null : session.launcher.discover(LauncherDiscoveryRequestBuilder.request().selectors(selectors).build())
        def legacy = records.findAll { it.legacy }.collect { r ->
            def request = Request.runner(new PushEnvironmentRunnerBuilder(environment).safeRunnerForClass(r.klass))
            if (r.method != null) request = request.filterWith(Description.createTestDescription(r.klass, r.method))
            def runner = request.runner
            if (runner.class.name == 'org.junit.internal.runners.ErrorReportingRunner') {
                throw new IllegalStateException("Cannot prepare ${r.klass.name} (${r.descriptor.source.nodeReference}): ${runner.description}")
            }
            [request: request, record: r]
        }
        def count = (plan == null ? 0 : plan.countTestIdentifiers { it.isTest() || it.source.orElse(null) instanceof org.junit.platform.engine.support.descriptor.MethodSource }) + legacy.sum(0) { it.request.runner.testCount() }
        if (count == 0) throw new IllegalStateException('No executable tests discovered for the selection')
        emit.accept([discovered: count])
        executing.run()
        if (plan != null) session.launcher.execute(plan, [listener] as TestExecutionListener[])
        legacy.each { item ->
            def statuses = [:]
            def publish = { d, status, detail ->
                emit.accept([id:'legacy:' + d.toString(), name:d.displayName, kind:d.isTest() ? 'TEST' : 'CONTAINER',
                    status:status, className:d.className, methodName:d.methodName,
                    source:sourceFor(d.className, d.methodName), detail:detail])
            }
            def core = new JUnitCore()
            core.addListener(new RunListener() {
                void testStarted(Description d) { publish(d, 'RUNNING', null) }
                void testFinished(Description d) { if (!statuses.containsKey(d)) publish(d, 'PASSED', null) }
                void testIgnored(Description d) { publish(d, 'SKIPPED', null) }
                void testFailure(org.junit.runner.notification.Failure f) { statuses[f.description] = 'FAILED'; publish(f.description, 'FAILED', f.trace) }
                void testAssumptionFailure(org.junit.runner.notification.Failure f) { statuses[f.description] = 'ABORTED'; publish(f.description, 'ABORTED', f.trace) }
            })
            core.run(item.request)
        }
    } finally { session.close() }
} finally {
    Thread.currentThread().contextClassLoader = context
    tests.closeSession(mpsSession)
}
