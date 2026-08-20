package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.CodeResultResponse
import com.specificlanguages.mops.protocol.CodeRunRequest
import com.specificlanguages.mops.protocol.ConstraintEnforcement
import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyClassLoader
import groovy.lang.GroovyShell
import jetbrains.mps.project.Project
import org.codehaus.groovy.control.CompilerConfiguration
import java.io.File
import java.nio.file.Path

class CodeModeExecutor(private val access: MpsAccess, private val project: Project) {
    fun execute(request: CodeRunRequest): CodeResultResponse {
        rejectDependencyInjection(request.source)
        val loader = GroovyClassLoader(javaClass.classLoader, CompilerConfiguration())
        try {
            val binding = Binding()
            binding.setVariable("project", project)
            binding.setVariable("mops", CodeModeRoot(access, project, request.constraints))
            val result = GroovyShell(loader, binding, CompilerConfiguration()).evaluate(request.source, request.sourceName)
            return CodeResultResponse(CodeResultAdapter.render(result))
        } catch (failure: Throwable) {
            val line = failure.stackTrace.firstOrNull {
                it.fileName == request.sourceName || request.sourceName.endsWith("/${it.fileName}")
            }?.lineNumber
            val location = if (line != null && line > 0) "${request.sourceName}:$line" else request.sourceName
            throw IllegalStateException("$location: ${failure.message ?: failure.javaClass.name}", failure)
        } finally {
            loader.clearCache()
            loader.close()
        }
    }

    private fun rejectDependencyInjection(source: String) {
        require(!Regex("(?m)^\\s*@(?:groovy\\.lang\\.)?(?:Grab|Grapes|GrabConfig|GrabResolver)\\b").containsMatchIn(source)) {
            "Groovy dependency injection is disabled in Code Mode; use classes from the daemon/MPS classpath"
        }
    }
}

class CodeModeRoot(private val access: MpsAccess, private val project: Project, private val defaultConstraints: ConstraintEnforcement) {
    private var block: String? = null

    fun <T> read(body: Closure<T>): T = enter("read") { access.read { body.call(CodeReadServices(this, project)) } }

    fun <T> edit(options: Map<String, Any?>, body: Closure<T>): T {
        val constraints = options["constraints"]?.toString()?.let(ConstraintEnforcement::valueOf) ?: defaultConstraints
        return enter("edit") { access.write {
            val services = CodeEditServices(this, constraints, project)
            body.call(services).also { services.persist() }
        } }
    }

    fun <T> edit(body: Closure<T>): T = edit(emptyMap<String, Any>(), body)

    fun makeProject() = outside { access.extra { makeProject() } }
    fun makeModules(modules: List<String>) = outside { access.extra { makeModules(modules) } }
    @JvmOverloads
    fun renderNode(target: com.specificlanguages.mops.protocol.NodeTarget, allowReflective: Boolean = false) =
        outside { access.extra { renderNode(target, allowReflective) } }

    @JvmOverloads
    fun help(path: String? = null): String = CodeCatalog.text(path)

    private fun <T> enter(kind: String, action: () -> T): T {
        check(block == null) { "Access Blocks cannot be nested; the current $block block already owns model access" }
        block = kind
        return try { action() } finally { block = null }
    }

    private fun <T> outside(action: () -> T): T {
        check(block == null) { "this operation must run outside an Access Block" }
        return action()
    }
}

open class CodeReadServices(
    private val delegate: com.specificlanguages.mops.daemon.core.MpsRead,
    protected val project: Project,
) : com.specificlanguages.mops.daemon.core.MpsRead by delegate {
    fun getModule(target: String): ModuleHandle = getModule(listOf(target))
    fun getModule(target: List<String>): ModuleHandle {
        require(target.size == 1) { "module Navigation Target must contain exactly one segment" }
        val value = target.single()
        val persistence = org.jetbrains.mps.openapi.persistence.PersistenceFacade.getInstance()
        val matches = project.repository.modules.filter {
            it.moduleName == value || persistence.asString(it.moduleReference) == value
        }
        require(matches.isNotEmpty()) { "module not found: $value" }
        require(matches.size == 1) { "ambiguous module: $value" }
        return moduleHandle(matches.single())
    }
}

class CodeEditServices(
    private val delegate: com.specificlanguages.mops.daemon.core.MpsWrite,
    private val constraints: ConstraintEnforcement,
    project: Project,
) : CodeReadServices(delegate, project), com.specificlanguages.mops.daemon.core.MpsWrite by delegate {
    private val creator = ModuleCreator(project)
    fun modelEdit(batch: com.specificlanguages.mops.protocol.EditBatch) = delegate.modelEdit(batch, constraints)

    @JvmOverloads fun createLanguage(moduleName: String, options: Map<String, Any?> = emptyMap()): LanguageHandle {
        options.requireOnly("descriptor", "withGenerator")
        val response = creator.createLanguage(com.specificlanguages.mops.protocol.CreateLanguageRequest(
            "", moduleName, options["descriptor"]?.toString(), options["withGenerator"] as? Boolean ?: false,
        ))
        return getModule(response.report!!.primary.moduleReference) as LanguageHandle
    }

    @JvmOverloads fun createSolution(moduleName: String, options: Map<String, Any?> = emptyMap()): SolutionHandle {
        options.requireOnly("descriptor", "usagePreset")
        val preset = options["usagePreset"]?.toString()?.replace('-', '_')?.uppercase()
            ?.let(com.specificlanguages.mops.protocol.SolutionUsagePreset::valueOf)
            ?: com.specificlanguages.mops.protocol.SolutionUsagePreset.NOT_GENERATED
        val response = creator.createSolution(com.specificlanguages.mops.protocol.CreateSolutionRequest(
            "", moduleName, options["descriptor"]?.toString(), preset,
        ))
        return getModule(response.report!!.primary.moduleReference) as SolutionHandle
    }

    @JvmOverloads fun createDevkit(moduleName: String, options: Map<String, Any?> = emptyMap()): DevkitHandle {
        options.requireOnly("descriptor")
        val response = creator.createDevkit(com.specificlanguages.mops.protocol.CreateDevkitRequest(
            "", moduleName, options["descriptor"]?.toString(),
        ))
        return getModule(response.report!!.primary.moduleReference) as DevkitHandle
    }

    @JvmOverloads fun createGenerator(language: LanguageHandle, alias: String, options: Map<String, Any?> = emptyMap()): GeneratorHandle {
        options.requireOnly("standalone", "descriptor")
        val response = creator.createGenerator(com.specificlanguages.mops.protocol.CreateGeneratorRequest(
            "", language.moduleReference, alias, options["standalone"] as? Boolean ?: false, options["descriptor"]?.toString(),
        ))
        return getModule(response.report!!.primary.moduleReference) as GeneratorHandle
    }

    internal fun persist() = creator.persist()
}

private fun Map<String, Any?>.requireOnly(vararg names: String) {
    val unknown = keys - names.toSet()
    require(unknown.isEmpty()) { "unknown named option(s): ${unknown.sorted().joinToString()}" }
}

private object CodeResultAdapter {
    fun render(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is Char, is Boolean, is Number, is File, is Path -> value.toString()
        is Map<*, *> -> value.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { json(it.key.toString()) + ":" + renderJson(it.value) }
        is Iterable<*> -> value.joinToString(separator = ",", prefix = "[", postfix = "]") { renderJson(it) }
        is Array<*> -> value.joinToString(separator = ",", prefix = "[", postfix = "]") { renderJson(it) }
        is BooleanArray -> value.joinToString(separator = ",", prefix = "[", postfix = "]")
        is IntArray -> value.joinToString(separator = ",", prefix = "[", postfix = "]")
        is LongArray -> value.joinToString(separator = ",", prefix = "[", postfix = "]")
        is DoubleArray -> value.joinToString(separator = ",", prefix = "[", postfix = "]")
        else -> error("unsupported Code Mode result ${value.javaClass.name}; return a string, primitive, map, collection, or array")
    }

    private fun renderJson(value: Any?): String = when (value) {
        null -> "null"
        is String, is Char, is File, is Path -> json(value.toString())
        is Boolean, is Number -> value.toString()
        is Map<*, *>, is Iterable<*>, is Array<*>, is BooleanArray, is IntArray, is LongArray, is DoubleArray -> render(value)!!
        else -> error("unsupported Code Mode result ${value.javaClass.name}; return a supported representation")
    }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        } }
        append('"')
    }
}
