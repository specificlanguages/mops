package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsExtra
import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.daemon.core.MpsWrite
import com.specificlanguages.mops.protocol.CodeCatalogRequest
import com.specificlanguages.mops.protocol.CodeCatalogResponse

object CodeCatalog {
    private data class Operation(val path: String, val signature: String, val access: String)

    private val operations: List<Operation> = buildList {
        reflect("mops.read", MpsRead::class.java, "read")
        reflect("mops.edit", MpsRead::class.java, "edit")
        reflect("mops.edit", MpsWrite::class.java, "edit")
        reflect("mops", MpsExtra::class.java, "outside")
        add(Operation("mops.help", "mops.help(String path = null)", "outside"))
    }.distinctBy { it.path }.sortedBy { it.path }

    private fun MutableList<Operation>.reflect(prefix: String, type: Class<*>, access: String) {
        type.declaredMethods.filterNot { it.isSynthetic }.forEach { method ->
            val parameters = method.parameters.joinToString(", ") { it.parameterizedType.typeName.substringAfterLast('.') }
            add(Operation("$prefix.${method.name}", "$prefix.${method.name}($parameters)", access))
        }
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
