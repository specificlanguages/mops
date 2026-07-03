package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ChildPosition
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.NodeTarget
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.language.ConceptRegistry
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

class EditBatchExecutor(
    private val modelNodeResolver: ModelNodeResolver,
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun apply(
        project: Project,
        batch: EditBatch,
        writeScope: WriteTransaction.WriteScope,
    ): ModelEditResponse {
        if (batch.operations.isEmpty()) {
            throw MpsRequestException(
                code = MpsErrorCode.INVALID_REQUEST,
                message = "edit batch must contain at least one operation",
            )
        }

        val affectedModels = linkedSetOf<EditableSModel>()
        var mutated = false

        fun fail(code: MpsErrorCode, message: String): Nothing {
            if (mutated) {
                reload(affectedModels)
            }
            throw MpsRequestException(code = code, message = message)
        }

        // Resolves a non-alias target to a node whose model is editable, clean at batch start, and tracked as affected.
        fun requireEditableNode(index: Int, target: EditTarget): SNode {
            if (target is EditTarget.Alias) {
                fail(
                    MpsErrorCode.UNSUPPORTED_TARGET,
                    "operation $index alias targets are not supported by this edit slice: ${target.alias}",
                )
            }
            val node = try {
                resolveTarget(project, target)
            } catch (exception: Exception) {
                fail(
                    MpsErrorCode.TARGET_RESOLUTION_FAILED,
                    "operation $index target could not be resolved: ${exception.message ?: exception.javaClass.name}",
                )
            } ?: fail(MpsErrorCode.NODE_NOT_FOUND, "operation $index target node not found")
            val model = node.model
                ?: fail(MpsErrorCode.NODE_NOT_FOUND, "operation $index target node is detached from a model")
            val editableModel = writeScope.asEditable(model)
                ?: fail(
                    MpsErrorCode.MODEL_READ_ONLY,
                    "operation $index target model is not editable: ${model.name.longName}",
                )
            if (editableModel !in affectedModels) {
                if (editableModel.isChanged) {
                    fail(
                        MpsErrorCode.MODEL_CHANGED,
                        "operation $index target model has unsaved changes: ${editableModel.name.longName}",
                    )
                }
                affectedModels.add(editableModel)
            }
            return node
        }

        fun resolveConceptOrFail(index: Int, fqn: String): SConcept {
            val concept = ConceptRegistry.getInstance().getConceptByName(fqn)
            if (!concept.isValid) {
                fail(MpsErrorCode.CONCEPT_NOT_FOUND, "operation $index concept not found: $fqn")
            }
            return concept as? SConcept
                ?: fail(MpsErrorCode.MODEL_EDIT_FAILED, "operation $index concept is not instantiable: $fqn")
        }

        fun resolveContainmentOrFail(index: Int, parent: SNode, role: String): SContainmentLink {
            val links = parent.concept.containmentLinks.filter { it.name == role }
            return when (links.size) {
                1 -> links.single()
                0 -> fail(
                    MpsErrorCode.ROLE_NOT_FOUND,
                    "operation $index containment role not found: $role on ${parent.concept.qualifiedName}",
                )
                else -> fail(
                    MpsErrorCode.AMBIGUOUS_ROLE,
                    "operation $index ambiguous containment role $role on ${parent.concept.qualifiedName}",
                )
            }
        }

        fun resolveReferenceLinkOrFail(index: Int, node: SNode, role: String): SReferenceLink {
            val links = node.concept.referenceLinks.filter { it.name == role }
            return when (links.size) {
                1 -> links.single()
                0 -> fail(
                    MpsErrorCode.ROLE_NOT_FOUND,
                    "operation $index reference role not found: $role on ${node.concept.qualifiedName}",
                )
                else -> fail(
                    MpsErrorCode.AMBIGUOUS_ROLE,
                    "operation $index ambiguous reference role $role on ${node.concept.qualifiedName}",
                )
            }
        }

        fun resolvePropertyOrFail(index: Int, node: SNode, name: String): SProperty =
            when (val resolution = resolveProperty(node, name)) {
                is PropertyResolution.Found -> resolution.property
                PropertyResolution.Missing -> fail(
                    MpsErrorCode.PROPERTY_NOT_FOUND,
                    "operation $index property not found: $name on ${node.concept.qualifiedName}",
                )
                is PropertyResolution.Ambiguous -> fail(
                    MpsErrorCode.AMBIGUOUS_PROPERTY,
                    "operation $index ambiguous property $name on ${node.concept.qualifiedName}: " +
                        resolution.properties.joinToString { it.name },
                )
            }

        fun resolveReferenceTargetOrFail(index: Int, ownerModel: SModel, reference: MpsNodeReferenceJson): SNode {
            val targetModel = reference.target.model
            val nodeId = reference.target.node
                ?: fail(
                    MpsErrorCode.TARGET_RESOLUTION_FAILED,
                    "operation $index reference ${reference.role} is missing a target node id",
                )
            val resolved = if (targetModel == null) {
                runCatching { ownerModel.getNode(persistence.createNodeId(nodeId)) }.getOrNull()
            } else {
                modelNodeResolver.findNode(project, NodeTarget.NodeReference("$targetModel/$nodeId"))
            }
            return resolved
                ?: fail(
                    MpsErrorCode.TARGET_RESOLUTION_FAILED,
                    "operation $index reference target not found: ${targetModel ?: ownerModel.name.value}/$nodeId",
                )
        }

        fun attach(index: Int, parent: SNode, link: SContainmentLink, child: SNode, position: ChildPosition) {
            val existing = parent.getChildren(link).toList()
            when (position) {
                ChildPosition.Last -> parent.addChild(link, child)
                ChildPosition.First ->
                    existing.firstOrNull()?.let { parent.insertChildBefore(link, child, it) }
                        ?: parent.addChild(link, child)
                is ChildPosition.Index ->
                    existing.getOrNull(position.index)?.let { parent.insertChildBefore(link, child, it) }
                        ?: parent.addChild(link, child)
                ChildPosition.Only -> fail(
                    MpsErrorCode.INVALID_REQUEST,
                    "operation $index position 'only' addresses an existing child and cannot place a new node",
                )
            }
        }

        // Sets properties and references on [node] and builds its nested children, recursively.
        fun populate(
            index: Int,
            model: SModel,
            node: SNode,
            properties: List<MpsNodePropertyJson>?,
            references: List<MpsNodeReferenceJson>?,
            children: List<MpsNodeJson>?,
        ) {
            properties?.forEach { property ->
                SNodeAccessUtil.setPropertyValue(node, resolvePropertyOrFail(index, node, property.name), property.value)
            }
            references?.forEach { reference ->
                node.setReferenceTarget(
                    resolveReferenceLinkOrFail(index, node, reference.role),
                    resolveReferenceTargetOrFail(index, model, reference),
                )
            }
            children?.forEach { childSpec ->
                val role = childSpec.role
                    ?: fail(MpsErrorCode.INVALID_REQUEST, "operation $index inline child is missing a role")
                val link = resolveContainmentOrFail(index, node, role)
                val childNode = model.createNode(resolveConceptOrFail(index, childSpec.concept))
                node.addChild(link, childNode)
                populate(index, model, childNode, childSpec.properties, childSpec.references, childSpec.children)
            }
        }

        for ((index, operation) in batch.operations.withIndex()) {
            val node = requireEditableNode(index, operation.target)

            when (operation) {
                is EditOperation.SetProperty -> {
                    val property = resolvePropertyOrFail(index, node, operation.name)
                    try {
                        SNodeAccessUtil.setPropertyValue(node, property, operation.value)
                        mutated = true
                    } catch (exception: Exception) {
                        fail(
                            MpsErrorCode.MODEL_EDIT_FAILED,
                            "operation $index failed: ${exception.message ?: exception.javaClass.name}",
                        )
                    }
                }

                is EditOperation.Delete -> {
                    mutated = true
                    node.delete()
                }

                is EditOperation.DeleteChild -> {
                    val child = locateChild(node, operation.role, operation.position)
                        ?: fail(
                            MpsErrorCode.NODE_NOT_FOUND,
                            "operation $index child not found: role ${operation.role}, position ${operation.position}",
                        )
                    mutated = true
                    child.delete()
                }

                is EditOperation.AddChild -> {
                    val link = resolveContainmentOrFail(index, node, operation.role)
                    val concept = resolveConceptOrFail(index, operation.concept)
                    val model = node.model!!
                    mutated = true
                    val child = model.createNode(concept)
                    attach(index, node, link, child, operation.position)
                    populate(index, model, child, operation.properties, operation.references, operation.children)
                }

                is EditOperation.MoveNode -> {
                    val newParent = requireEditableNode(index, operation.into)
                    var ancestor: SNode? = newParent
                    while (ancestor != null) {
                        if (ancestor == node) {
                            fail(
                                MpsErrorCode.INVALID_REQUEST,
                                "operation $index cannot move a node into itself or its own descendant",
                            )
                        }
                        ancestor = ancestor.parent
                    }
                    val link = resolveContainmentOrFail(index, newParent, operation.role)
                    mutated = true
                    val oldParent = node.parent
                    if (oldParent != null) {
                        oldParent.removeChild(node)
                    } else {
                        node.model?.removeRootNode(node)
                    }
                    attach(index, newParent, link, node, operation.position)
                }
            }
        }

        return when (val saveOutcome = writeScope.saveWithResolveInfo(affectedModels)) {
            SaveOutcome.Saved -> ModelEditResponse(created = emptyMap(), violations = emptyList())
            is SaveOutcome.SaveFailed -> fail(
                MpsErrorCode.SAVE_FAILED,
                "model save failed for ${saveOutcome.model.name.longName}: ${saveOutcome.result}",
            )
        }
    }

    private fun locateChild(
        node: SNode,
        role: String,
        position: ChildPosition,
    ): SNode? {
        val childrenInRole = node.children.filter { it.containmentLink?.name == role }

        return when (position) {
            is ChildPosition.First -> childrenInRole.firstOrNull()
            is ChildPosition.Last -> childrenInRole.lastOrNull()
            is ChildPosition.Only -> childrenInRole.singleOrNull()
            is ChildPosition.Index -> childrenInRole.elementAtOrNull(position.index)
        }
    }

    private fun resolveTarget(project: Project, target: EditTarget): SNode? =
        when (target) {
            is EditTarget.Alias -> error("alias targets must be rejected before resolution")
            is EditTarget.InModel -> modelNodeResolver.findNode(
                project,
                NodeTarget.InModel(modelTarget = target.modelTarget, nodeId = target.nodeId),
            )

            is EditTarget.NodeReference -> modelNodeResolver.findNode(
                project,
                NodeTarget.NodeReference(target.nodeReference),
            )
        }

    private fun resolveProperty(node: SNode, name: String): PropertyResolution {
        val properties = node.concept.properties.filter { it.name == name }.toList()
        return when (properties.size) {
            0 -> PropertyResolution.Missing
            1 -> PropertyResolution.Found(properties.single())
            else -> PropertyResolution.Ambiguous(properties)
        }
    }

    private fun reload(models: Iterable<EditableSModel>) {
        models.forEach { it.reloadFromSource() }
    }
}

private sealed interface PropertyResolution {
    data class Found(val property: SProperty) : PropertyResolution
    data class Ambiguous(val properties: List<SProperty>) : PropertyResolution
    data object Missing : PropertyResolution
}
