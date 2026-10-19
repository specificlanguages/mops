import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.jvm.toolchain.internal.SpecificInstallationToolchainSpec
import org.gradle.kotlin.dsl.support.serviceOf
import java.util.zip.ZipFile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Used by checkDaemonRelocation to walk live class references without tripping over dead constant-pool strings.
        classpath(libs.asm)
        classpath(libs.asm.commons)
    }
}

plugins {
    id("mops.kotlin-jvm-conventions")
    application

    alias(libs.plugins.mps.platform.cache)
    alias(libs.plugins.jbr.toolchain)
}

val antTestAdapters = configurations.register("antTestAdapters") { isCanBeConsumed = false }

val mpsZip = configurations.register("mpsZip") { isCanBeConsumed = false }
val mpsRuntime = configurations.register("mpsRuntime") { isCanBeConsumed = false }
val mpsPlugin = configurations.register("mpsPlugin") {
    isCanBeConsumed = false
    isTransitive = false
}

configurations {
    compileOnly { extendsFrom(mpsRuntime) }
    testCompileOnly { extendsFrom(mpsRuntime) }
}

dependencies {
    antTestAdapters(project(path = ":ant-test-adapters", configuration = "adapters"))
    implementation(project(":daemon-core"))
    implementation(project(":protocol"))
    implementation(project(":launcher"))
    implementation(libs.picocli)
    implementation(libs.groovy)
    implementation(libs.project.loader)

    mpsPlugin(project(":daemon-mps-plugin"))

    jbr(libs.mps.jbr)

    testImplementation("org.apache.groovy:groovy-json:${libs.versions.groovy.get()}")
    testImplementation(libs.system.lambda)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    mpsRuntime(mpsZip.map {
        zipTree(it.singleFile).matching {
            include("lib/mps-core.jar")
            // ModelGenerationStatusManager, which detects whether a module is built from stale sources (see
            // ModuleLoadDiagnostics and the mps-api-research model-generation-status note).
            include("lib/mps-generator.jar")
            // CheckerRegistry, which drives the full Model Check (see ModelChecker).
            include("lib/mps-project-check.jar")
            // Editor cell rendering (headless node-to-text). See the mps-api-research editor-cell-rendering note.
            include("lib/mps-editor.jar")
            include("lib/mps-editor-api.jar")
            include("lib/mps-collections.jar")
            include("lib/mps-closures.jar")
            include("lib/mps-environment.jar")
            include("lib/mps-persistence.jar")
            include("lib/mps-platform.jar")
            include("lib/mps-openapi.jar")
            include("lib/mpsant/mps-tool.jar")
            include("lib/mps-references.jar")
            include("lib/mps-constraints-runtime.jar")
            include("lib/util.jar")
            include("lib/util-8.jar")
            include("lib/util_rt.jar")
            include("lib/testFramework.jar")
            include("lib/app.jar")
            include("lib/intellij.platform.core.jar")
            include("lib/intellij.platform.core.impl.jar")
            include("lib/intellij.platform.core.ui.jar")
            include("lib/intellij.platform.ide.core.jar")
            include("lib/intellij.platform.ide.impl.jar")
            include("lib/intellij.platform.projectModel.jar")
            include("lib/intellij.libraries.kotlinx.coroutines.core.jar")
            include("languages/baseLanguage/jetbrains.mps.baseLanguage.jar")
            include("languages/baseLanguage/jetbrains.mps.baseLanguage.javadoc.jar")
            include("languages/baseLanguage/jetbrains.mps.baseLanguage.methodReferences.jar")
            include("languages/baseLanguage/jetbrains.mps.baseLanguage.scopes.jar")
        }
    })
    mpsZip(libs.mps.distribution)
}

application {
    applicationName = "mops-daemon"
    mainClass = "com.specificlanguages.mops.daemon.MainKt"
}

sourceSets.test {
    resources.srcDir(rootProject.layout.projectDirectory.dir("skills"))
}

val testMpsRoot = mpsPlatformCache.getMpsRoot(mpsZip)

// Keep in sync with MpsLaunchArgs.MPS_ADD_OPENS in the launcher project: --add-opens must be a JVM launch
// argument, so the test JVM cannot apply the launcher's list at runtime the way it applies -D properties.
val mpsAddOpens = listOf(
    "java.base/java.io",
    "java.base/java.lang",
    "java.base/java.lang.reflect",
    "java.base/java.net",
    "java.base/java.nio",
    "java.base/java.nio.charset",
    "java.base/java.text",
    "java.base/java.time",
    "java.base/java.util",
    "java.base/java.util.concurrent",
    "java.base/java.util.concurrent.atomic",
    "java.base/jdk.internal.ref",
    "java.base/jdk.internal.vm",
    "java.base/sun.nio.ch",
    "java.base/sun.nio.fs",
    "java.base/sun.security.ssl",
    "java.base/sun.security.util",
    "java.desktop/java.awt",
    "java.desktop/java.awt.dnd.peer",
    "java.desktop/java.awt.event",
    "java.desktop/java.awt.image",
    "java.desktop/java.awt.peer",
    "java.desktop/javax.swing",
    "java.desktop/javax.swing.plaf.basic",
    "java.desktop/javax.swing.text.html",
    "java.desktop/sun.awt.datatransfer",
    "java.desktop/sun.awt.image",
    "java.desktop/sun.awt",
    "java.desktop/sun.font",
    "java.desktop/sun.java2d",
    "java.desktop/sun.swing",
    "jdk.attach/sun.tools.attach",
    "jdk.compiler/com.sun.tools.javac.api",
    "jdk.internal.jvmstat/sun.jvmstat.monitor",
    "jdk.jdi/com.sun.tools.jdi",
    "java.desktop/sun.lwawt",
    "java.desktop/sun.lwawt.macosx",
    "java.desktop/com.apple.laf",
    "java.desktop/com.apple.eawt",
    "java.desktop/com.apple.eawt.event",
    "java.management/sun.management",
).map { "--add-opens=$it=ALL-UNNAMED" }

// Configures a daemon test task to run against the given unpacked MPS distribution, with MPS state under
// build/<workDirName> so test tasks for different MPS versions can run side by side.
fun Test.configureMpsTest(mpsRoot: Provider<File>, workDirName: String) {
    // A full MPS environment with the distribution's bundled plugins needs more than Gradle's default 512m test heap.
    // The production daemon runs on the JVM's (much larger) default heap, so this ceiling only bounds the test JVM.
    maxHeapSize = "4g"
    // The MPS runtime jars come from the unpacked distribution, like the production daemon's classpath, so the
    // IntelliJ platform detects the MPS home from the jar locations and loads bundled plugins and languages from it.
    classpath += files(
        mpsRoot.map { root ->
            fileTree(root) {
                include("lib/*.jar")
                // Exercise the bundled code-mode runtime in daemon tests.
                exclude("lib/groovy.jar")
                include("lib/modules/*.jar")
                include("lib/mpsant/mps-tool.jar")
            }
        },
    )
    inputs.files(mpsPlugin).withPropertyName("mpsPlugin")
    jvmArgs(mpsAddOpens)
    val mpsTestWorkDir = layout.buildDirectory.dir(workDirName)
    jvmArgumentProviders.add {
        // The IntelliJ platform's single-instance DirectoryLock is keyed on idea.config.path / idea.system.path, and
        // PathManager caches those on first access. So they must be launch-time -D arguments, not runtime
        // System.setProperty calls: a runtime set can lose the race against PathManager's caching and fall back to the
        // shared per-product default directory, at which point two test JVMs in different worktrees collide on one lock
        // ("Only one instance of MPS can be run at a time"). Rooting them under this module's build/ makes them
        // per-worktree and per-task, and stable across runs the way the production daemon's per-project dirs are.
        val work = mpsTestWorkDir.get().asFile
        val configDir = work.resolve("idea-config").apply { mkdirs() }
        val systemDir = work.resolve("idea-system").apply { mkdirs() }
        listOf(
            "-Dtest.mpsHome=${mpsRoot.get()}",
            "-Dtest.projectsDir=${rootDir.resolve("test-projects")}",
            "-Dtest.mpsPlugin=${mpsPlugin.get().singleFile}",
            "-Djava.awt.headless=true",
            "-Didea.home.path=${mpsRoot.get()}",
            "-Didea.config.path=$configDir",
            "-Didea.system.path=$systemDir",
            // Log synchronously instead of through the platform's AsyncLog. AsyncLog dispatches records to a coroutine
            // over a channel and closes that channel when the application is disposed. The environment is disposed
            // during JVM shutdown (see SharedMpsEnvironment), but platform background threads (coroutine dispatchers
            // finalizing cancelled jobs, the AppDelayQueue "Periodic tasks thread") outlive that dispose and keep
            // logging. Their records hit the already-closed channel, AsyncLog.log's check(trySend().isSuccess) throws,
            // and the uncaught IllegalStateException prints a stack trace per thread to stderr after the tests finish.
            // The synchronous path has no channel and no such race. Read at AsyncLogKt.<clinit>, so it must be a
            // launch-time -D, not a runtime System.setProperty.
            "-Dintellij.platform.log.sync=true",
        )
    }
}

tasks.test {
    javaLauncher = jbrToolchain.javaLauncher
    configureMpsTest(testMpsRoot, "mps-test")
}

val jbrOs = when {
    System.getProperty("os.name").startsWith("Windows") -> "windows"
    System.getProperty("os.name").startsWith("Mac") -> "osx"
    System.getProperty("os.name").startsWith("Linux") -> "linux"
    else -> error("Unsupported JBR operating system: ${System.getProperty("os.name")}")
}
val jbrArch = when (System.getProperty("os.arch")) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64" -> "x64"
    else -> error("Unsupported JBR architecture: ${System.getProperty("os.arch")}")
}

// The daemon compiles against the pinned MPS version but runs on every supported one. These tasks run the same test
// classes on each supported version's distribution and matching JBR.
val supportedMpsVersions = providers.gradleProperty("supportedMpsVersions").get().split(',').map(String::trim)
val mpsVersionTests = supportedMpsVersions.map { mpsVersion ->
    val mps = configurations.register("testMps$mpsVersion") {
        isCanBeConsumed = false
    }
    val jbr = configurations.register("testJbr$mpsVersion") {
        isCanBeConsumed = false
        resolutionStrategy.dependencySubstitution {
            all {
                val selector = requested
                // The MPS marker selects the JBR version; only the actual JBR archive needs a host classifier.
                if (selector is ModuleComponentSelector && selector.group == "com.jetbrains.jdk") {
                    artifactSelection { selectArtifact("tgz", null, "$jbrOs-$jbrArch") }
                }
            }
        }
    }
    dependencies.add(mps.name, "com.jetbrains:mps:$mpsVersion")
    dependencies.add(jbr.name, "com.jetbrains.mps:mps-jbr:$mpsVersion")
    val mpsRoot = mpsPlatformCache.getMpsRoot(mps)
    val jbrHome = mpsPlatformCache.getJbrRoot(jbr).map {
        if (jbrOs == "osx") it.resolve("Contents/Home") else it
    }
    tasks.register<Test>("testMps$mpsVersion") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs daemon tests against MPS $mpsVersion and its matching JBR."
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        configureMpsTest(mpsRoot, "mps-test-$mpsVersion")
        // Gradle has no public API for a toolchain at a given Java home; the jbr-toolchain plugin uses the same
        // internal spec for the pinned JBR.
        javaLauncher = jbrHome.flatMap {
            javaToolchains.launcherFor(SpecificInstallationToolchainSpec.fromJavaHome(serviceOf<PropertyFactory>(), it))
        }
    }
}

tasks.register("testAllMpsVersions") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs daemon tests against every supported MPS version."
    dependsOn(mpsVersionTests)
}

val dist = configurations.consumable("dist") {
    outgoing.artifact(tasks.installDist)
}

// Enforce that nothing on the daemon's runtime classpath carries a non-relocated kotlinx.serialization. On the daemon's
// flat classpath (our jars first, then MPS's), a stray non-relocated copy would shadow MPS's own serialization runtime.
// Our copy must only ever appear under the relocated `com.specificlanguages.mops.shaded.kotlinx.serialization` package.
val checkDaemonRelocation by tasks.registering {
    val runtimeClasspath = configurations.runtimeClasspath
    inputs.files(runtimeClasspath).withNormalizer(ClasspathNormalizer::class)

    doLast {
        val forbiddenPrefix = "kotlinx/serialization/"
        val violations = mutableListOf<String>()

        runtimeClasspath.get().files.filter { it.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                    // A leaked non-relocated runtime jar would carry class files under the forbidden package.
                    if (entry.name.startsWith(forbiddenPrefix)) {
                        violations += "${jar.name}: ships non-relocated class ${entry.name}"
                        return@forEach
                    }

                    val referenced = sortedSetOf<String>()
                    val remapper = object : org.objectweb.asm.commons.Remapper() {
                        override fun map(internalName: String): String {
                            if (internalName.startsWith(forbiddenPrefix)) {
                                referenced += internalName
                            }
                            return internalName
                        }
                    }
                    try {
                        zip.getInputStream(entry).use { input ->
                            org.objectweb.asm.ClassReader(input).accept(
                                org.objectweb.asm.commons.ClassRemapper(
                                    org.objectweb.asm.ClassWriter(0),
                                    remapper,
                                ),
                                0,
                            )
                        }
                    } catch (e: Exception) {
                        // Skip anything ASM cannot parse (e.g. non-standard or future-version class files).
                    }
                    referenced.forEach { name ->
                        violations += "${jar.name}: ${entry.name} references non-relocated $name"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Non-relocated kotlinx.serialization found on the daemon runtime classpath:")
                    violations.sorted().forEach { appendLine("  - $it") }
                    appendLine("All kotlinx.serialization usage must be relocated under the shaded package.")
                }
            )
        }
    }
}

tasks.check {
    dependsOn(checkDaemonRelocation)
}

// Nested JARs retain the separate Ant/application and MPS module classloader boundaries.
tasks.processResources {
    from(antTestAdapters) { into("testing/ant") }
}
