package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.CodeResultResponse
import com.specificlanguages.mops.protocol.CodeRunRequest
import groovy.lang.Binding
import groovy.lang.GroovyClassLoader
import groovy.lang.GroovyShell
import jetbrains.mps.project.MPSProject
import org.codehaus.groovy.control.CompilerConfiguration
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.io.File
import java.nio.file.Path

class CodeModeExecutor(private val access: MpsAccess, private val project: MPSProject) {
    fun execute(request: CodeRunRequest): CodeResultResponse {
        rejectDependencyInjection(request.source)
        val configuration = CompilerConfiguration().apply { scriptBaseClass = CodeModeScript::class.java.name }
        val loader = GroovyClassLoader(javaClass.classLoader, configuration)
        val mops = Mops(project, access)
        CodeModeProjectProvider.initialize(mops)
        try {
            val result = GroovyShell(loader, Binding(), configuration).evaluate(request.source, request.sourceName)
            return CodeResultResponse(CodeResultAdapter.render(result))
        } catch (failure: Throwable) {
            val line = failure.stackTrace.firstOrNull {
                it.fileName == request.sourceName || request.sourceName.endsWith("/${it.fileName}")
            }?.lineNumber
            val location = if (line != null && line > 0) "${request.sourceName}:$line" else request.sourceName
            throw IllegalStateException("$location: ${failure.message ?: failure.javaClass.name}", failure)
        } finally {
            CodeModeProjectProvider.clear(mops)
            loader.clearCache()
            loader.close()
        }
    }

    private fun rejectDependencyInjection(source: String) {
        require(!Regex("(?m)^\\s*@(?:groovy\\.lang\\.)?(?:Grab|Grapes|GrabConfig|GrabResolver)\\b").containsMatchIn(source)) {
            "Groovy dependency injection is disabled in code mode; use classes from the daemon/MPS classpath"
        }
    }
}

internal object CodeResultAdapter {
    private val persistence get() = PersistenceFacade.getInstance()

    fun render(value: Any?): String? = when (value) {
        null -> null
        is SNode -> value.reference.let(persistence::asString)
        is SModel -> value.reference.let(persistence::asString)
        is SModule -> value.moduleReference.let(persistence::asString)
        is String -> value
        is Char, is Boolean, is Number, is File, is Path -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + renderJson(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { renderJson(it) }
        is Array<*> -> value.joinToString(",", "[", "]") { renderJson(it) }
        is BooleanArray -> value.joinToString(",", "[", "]")
        is IntArray -> value.joinToString(",", "[", "]")
        is LongArray -> value.joinToString(",", "[", "]")
        is DoubleArray -> value.joinToString(",", "[", "]")
        else -> error("unsupported code mode result ${value.javaClass.name}; return a string, primitive, MPS object, map, collection, or array")
    }

    private fun renderJson(value: Any?): String = when (value) {
        null -> "null"
        is SNode, is SModel, is SModule -> render(value)?.let(::json) ?: "null"
        is String, is Char, is File, is Path -> json(value.toString())
        is Boolean, is Number -> value.toString()
        is Map<*, *>, is Iterable<*>, is Array<*>, is BooleanArray, is IntArray, is LongArray, is DoubleArray -> render(value)!!
        else -> error("unsupported code mode result ${value.javaClass.name}; return a supported representation")
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
