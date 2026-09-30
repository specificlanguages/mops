package com.specificlanguages.mops.daemon

import java.lang.reflect.Method
import java.util.Properties

/** The Groovy extension module is the source of truth for registered API methods and their access annotations. */
internal object CodeHelpApi {
    val methods: List<Method> by lazy {
        val properties = Properties()
        CodeHelpApi::class.java.classLoader.getResourceAsStream("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule")!!.use(properties::load)
        properties.getProperty("extensionClasses").split(',').flatMap { name ->
            Class.forName(name, false, CodeHelpApi::class.java.classLoader).methods.filter { it.isAnnotationPresent(CodeModeExtension::class.java) }
        }.distinctBy { it.toGenericString() }
    }

    fun access(receiver: String, name: String, fallback: String): String {
        val method = methods.firstOrNull { receiverName(it.parameterTypes.first().simpleName) == receiver && memberName(it.name) == name }
        return method?.getAnnotation(CodeModeExtension::class.java)?.access?.name?.lowercase() ?: fallback
    }

    fun path(method: Method): String = "${receiverName(method.parameterTypes.first().simpleName)}.${memberName(method.name)}"

    private fun receiverName(name: String) = when (name) {
        "MopsEditing" -> "mops.editing"
        "MopsEditingBuild" -> "mops.editing.build"
        "MopsParsing" -> "mops.parsing"
        "MopsSearch" -> "mops.search"
        "MopsLookup" -> "mops.lookup"
        "MopsTesting" -> "mops.testing"
        else -> name
    }

    private fun memberName(name: String): String = if (name.startsWith("get")) name.removePrefix("get").replaceFirstChar { it.lowercase() } else name
}
