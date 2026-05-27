package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

class JsonNodeExporter(
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun export(node: SNode, includeModel: Boolean = false): Map<String, Any?> =
        export(node = node, includeModel = includeModel, includeRole = false)

    private fun export(node: SNode, includeModel: Boolean, includeRole: Boolean): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val model = node.model
        if (includeModel) {
            model?.let { result["model"] = persistence.asString(it.reference) }
        }
        if (includeRole) {
            node.containmentLink?.let { result["role"] = it.role }
        }
        result["concept"] = node.concept.qualifiedName
        result["id"] = persistence.asString(node.nodeId)

        val properties = node.properties
            .mapNotNull { property ->
                node.getProperty(property)?.let { value -> property.name to value }
            }
            .sortedBy { it.first }
            .toMap(LinkedHashMap())
        if (properties.isNotEmpty()) {
            result["properties"] = properties
        }

        val references = node.references
            .map { reference ->
                val target = linkedMapOf<String, Any?>()
                val targetModel = reference.targetSModelReference
                if (targetModel != null && targetModel != model?.reference) {
                    target["model"] = persistence.asString(targetModel)
                }
                reference.targetNodeId?.let { target["node"] = persistence.asString(it) }
                linkedMapOf(
                    "role" to reference.link.role,
                    "target" to target,
                )
            }
            .sortedBy { it["role"] as String }
        if (references.isNotEmpty()) {
            result["references"] = references
        }

        val children = node.children
            .map { export(node = it, includeModel = false, includeRole = true) }
            .toList()
        if (children.isNotEmpty()) {
            result["children"] = children
        }

        return result
    }
}
