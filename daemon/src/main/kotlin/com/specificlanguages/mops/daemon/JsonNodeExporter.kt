package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceTargetJson
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

class JsonNodeExporter(
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun export(node: SNode): MpsNodeJson = export(node, includeModel = true, includeRole = false)

    private fun export(node: SNode, includeModel: Boolean, includeRole: Boolean): MpsNodeJson {
        val model = node.model

        val properties = node.properties
            .mapNotNull { property ->
                node.getProperty(property)?.let { value ->
                    MpsNodePropertyJson(name = property.name, value = value)
                }
            }
            .sortedBy { it.name }
            .takeIf { it.isNotEmpty() }

        val references = node.references
            .map { reference ->
                val targetModel = reference.targetSModelReference
                MpsNodeReferenceJson(
                    role = reference.link.name,
                    target = MpsNodeReferenceTargetJson(
                        model = targetModel
                            ?.takeIf { it != model?.reference }
                            ?.let(persistence::asString),
                        node = reference.targetNodeId?.let(persistence::asString),
                    ),
                )
            }
            .sortedBy { it.role }
            .takeIf { it.isNotEmpty() }

        val children = node.children
            .map { export(node = it, includeModel = false, includeRole = true) }
            .toList()
            .takeIf { it.isNotEmpty() }

        return MpsNodeJson(
            model = model
                ?.takeIf { includeModel }
                ?.let { persistence.asString(it.reference) },
            role = node.containmentLink
                ?.takeIf { includeRole }
                ?.role,
            concept = node.concept.qualifiedName,
            id = persistence.asString(node.nodeId),
            properties = properties,
            references = references,
            children = children,
        )
    }
}
