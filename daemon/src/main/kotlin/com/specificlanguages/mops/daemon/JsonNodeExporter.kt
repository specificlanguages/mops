package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceTargetJson
import jetbrains.mps.smodel.SNodeUtil
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
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
                // Best-effort resolution: a resolved target contributes its name and concept, an unresolvable one
                // (unloaded model, dangling reference) leaves them null while the address is still exported.
                val targetNode = reference.targetNode
                MpsNodeReferenceJson(
                    role = reference.link.name,
                    target = MpsNodeReferenceTargetJson(
                        model = targetModel
                            ?.takeIf { it != model?.reference }
                            ?.let(persistence::asString),
                        node = reference.targetNodeId?.let(persistence::asString),
                        name = targetNode?.let(::nodeName),
                        concept = targetNode?.concept?.qualifiedName,
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

    private fun nodeName(node: SNode): String? =
        SNodeAccessUtil.getPropertyValue(node, SNodeUtil.property_INamedConcept_name) as String?
}
