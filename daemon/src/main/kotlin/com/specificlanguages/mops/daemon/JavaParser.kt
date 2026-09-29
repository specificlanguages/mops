package com.specificlanguages.mops.daemon

import groovy.lang.GroovyObjectSupport
import org.jetbrains.mps.openapi.model.SNode
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** The attached output of one Java snippet parsing operation. */
class JavaParsingResult(
    val nodes: List<SNode>,
    val unresolved: List<SNode>,
) : AbstractMap<String, Any?>() {
    override val entries: Set<Map.Entry<String, Any?>> = mapOf("nodes" to nodes, "unresolved" to unresolved).entries
}

/** Parses Java source into native MPS destinations and resolves the inserted subtrees. */
class JavaSnippetParser : GroovyObjectSupport() {
    override fun invokeMethod(name: String, arguments: Any?): Any? {
        val args = when (arguments) {
            is Array<*> -> arguments
            null -> emptyArray()
            else -> arrayOf(arguments)
        }
        return when (name) {
            "addJavaClassesFromString" -> requireArguments(name, args, 2) { addJavaClassesFromString(it[0]!!, it[1] as String) }
            "addJavaMembersFromString" -> requireArguments(name, args, 2, 3) {
                addJavaMembersFromString(it[0]!!, it[1] as String, it.getOrNull(2))
            }
            "addJavaStatementsFromString" -> requireArguments(name, args, 2, 3) {
                addJavaStatementsFromString(it[0]!!, it[1] as String, it.getOrNull(2))
            }
            else -> super.invokeMethod(name, arguments)
        }
    }

    private fun requireArguments(name: String, arguments: Array<out Any?>, vararg counts: Int, call: (Array<out Any?>) -> Any?): Any? {
        require(arguments.size in counts) { "$name expects ${counts.joinToString(" or ")} arguments" }
        return call(arguments)
    }

    fun addJavaClassesFromString(model: Any, source: String): JavaParsingResult {
        requireCommand("mops.parsing.java.addJavaClassesFromString")
        return invokeBridge("classes", model, source, null)
    }

    fun addJavaMembersFromString(classifier: Any, source: String): JavaParsingResult =
        addJavaMembersFromString(classifier, source, null)

    fun addJavaMembersFromString(classifier: Any, source: String, beforeMember: Any?): JavaParsingResult {
        requireCommand("mops.parsing.java.addJavaMembersFromString")
        return invokeBridge("members", classifier, source, beforeMember)
    }

    fun addJavaStatementsFromString(statementList: Any, source: String): JavaParsingResult =
        addJavaStatementsFromString(statementList, source, null)

    fun addJavaStatementsFromString(statementList: Any, source: String, beforeStatement: Any?): JavaParsingResult {
        requireCommand("mops.parsing.java.addJavaStatementsFromString")
        return invokeBridge("statements", statementList, source, beforeStatement)
    }

    private fun invokeBridge(operation: String, destination: Any, source: String, anchor: Any?): JavaParsingResult {
        val result = try {
            bridgeMethod.invoke(null, operation, destination, source, anchor) as Array<*>
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
        @Suppress("UNCHECKED_CAST")
        return JavaParsingResult(result[0] as List<SNode>, result[1] as List<SNode>)
    }

    companion object {
        internal const val PLUGIN_ID = "com.specificlanguages.mops.daemon.plugin"
        internal const val BRIDGE_CLASS = "com.specificlanguages.mops.daemon.plugin.JavaParserBridge"

        private val bridgeMethod: Method by lazy(JavaParserPluginLoader::loadBridgeMethod)
    }
}
