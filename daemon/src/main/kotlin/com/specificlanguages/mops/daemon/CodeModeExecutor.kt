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
            binding.setVariable("mops", CodeModeRoot(access, request.constraints))
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

class CodeModeRoot(private val access: MpsAccess, private val defaultConstraints: ConstraintEnforcement) {
    private var block: String? = null

    fun <T> read(body: Closure<T>): T = enter("read") { access.read { body.call(this) } }

    fun <T> edit(options: Map<String, Any?>, body: Closure<T>): T {
        val constraints = options["constraints"]?.toString()?.let(ConstraintEnforcement::valueOf) ?: defaultConstraints
        return enter("edit") { access.write { body.call(CodeEditServices(this, constraints)) } }
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

class CodeEditServices(
    private val delegate: com.specificlanguages.mops.daemon.core.MpsWrite,
    private val constraints: ConstraintEnforcement,
) : com.specificlanguages.mops.daemon.core.MpsWrite by delegate {
    fun modelEdit(batch: com.specificlanguages.mops.protocol.EditBatch) = delegate.modelEdit(batch, constraints)
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
