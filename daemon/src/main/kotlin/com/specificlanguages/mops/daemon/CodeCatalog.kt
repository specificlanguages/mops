package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsExtra
import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.daemon.core.MpsWrite
import com.specificlanguages.mops.protocol.CodeCatalogRequest
import com.specificlanguages.mops.protocol.CodeCatalogResponse
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

object CodeCatalog {
    private data class Operation(val path: String, val signature: String, val access: String)

    private val operations: List<Operation> = buildList {
        reflect("mops.read", MpsRead::class.java, "read")
        reflect("mops.edit", MpsRead::class.java, "edit")
        reflect("mops.edit", MpsWrite::class.java, "edit")
        reflect("mops", MpsExtra::class.java, "outside")
        add(Operation("mops.read.getModule", "mops.read.getModule(NavigationTarget target)", "read"))
        add(Operation("mops.edit.getModule", "mops.edit.getModule(NavigationTarget target)", "edit"))
        add(Operation(
            "mops.edit.createLanguage",
            "mops.edit.createLanguage(String moduleName, descriptor: String = null, withGenerator: Boolean = false)",
            "edit",
        ))
        add(Operation(
            "mops.edit.createSolution",
            "mops.edit.createSolution(String moduleName, descriptor: String = null, usagePreset: String = 'not-generated')",
            "edit",
        ))
        add(Operation(
            "mops.edit.createDevkit",
            "mops.edit.createDevkit(String moduleName, descriptor: String = null)",
            "edit",
        ))
        add(Operation(
            "mops.edit.createGenerator",
            "mops.edit.createGenerator(LanguageHandle language, String alias, standalone: Boolean = false, descriptor: String = null)",
            "edit",
        ))
        add(Operation("mops.help", "mops.help(String path = null)", "outside"))
    }.distinctBy { it.path }.sortedBy { it.path }

    private fun MutableList<Operation>.reflect(prefix: String, type: Class<*>, access: String) {
        type.declaredMethods.filterNot { it.isSynthetic || '$' in it.name }.forEach { method ->
            val parameters = method.parameters.joinToString(", ") { renderType(it.parameterizedType) }
            add(Operation("$prefix.${method.name}", "$prefix.${method.name}($parameters)", access))
        }
    }

    private fun renderType(type: Type): String = when (type) {
        is Class<*> -> when {
            type.isArray -> "${renderType(type.componentType)}[]"
            type == java.lang.Boolean.TYPE -> "boolean"
            type == java.lang.Integer.TYPE -> "int"
            type == java.lang.Long.TYPE -> "long"
            else -> type.simpleName
        }
        is ParameterizedType -> buildString {
            append(renderType(type.rawType))
            append(type.actualTypeArguments.joinToString(", ", "<", ">", transform = ::renderType))
        }
        is WildcardType -> type.upperBounds.firstOrNull()?.takeUnless { it == Any::class.java }
            ?.let { "? extends ${renderType(it)}" } ?: "?"
        is GenericArrayType -> "${renderType(type.genericComponentType)}[]"
        else -> type.typeName.substringAfterLast('.')
    }

    fun response(request: CodeCatalogRequest) = CodeCatalogResponse(
        if (request.json) json(request.path) else text(request.path),
    )

    fun text(path: String?): String {
        val selected = select(path)
        return buildString {
            appendLine("Code Mode services")
            selected.forEach { appendLine("${it.signature}  [${it.access}]") }
        }.trimEnd()
    }

    private fun json(path: String?): String = select(path).joinToString(prefix = "[", postfix = "]") {
        "{\"path\":\"${it.path}\",\"signature\":\"${it.signature}\",\"access\":\"${it.access}\"}"
    }

    private fun select(path: String?): List<Operation> = if (path == null) operations else operations.filter {
        it.path == path || it.path.startsWith("$path.")
    }.also { require(it.isNotEmpty()) { "unknown Code Service path: $path" } }
}
