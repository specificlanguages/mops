package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ChildPosition
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.NodeTarget
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import jetbrains.mps.project.Project

class EditBatchExecutor(
    private val modelNodeResolver: ModelNodeResolver,
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

        for ((index, operation) in batch.operations.withIndex()) {
            val target = operation.target
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
            }
                ?: fail(MpsErrorCode.NODE_NOT_FOUND, "operation $index target node not found")
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

            when (operation) {
                is EditOperation.SetProperty -> {
                    val property = when (val resolution = resolveProperty(node, operation.name)) {
                        is PropertyResolution.Found -> resolution.property
                        PropertyResolution.Missing -> fail(
                            MpsErrorCode.PROPERTY_NOT_FOUND,
                            "operation $index property not found: ${operation.name} on ${node.concept.qualifiedName}",
                        )

                        is PropertyResolution.Ambiguous -> fail(
                            MpsErrorCode.AMBIGUOUS_PROPERTY,
                            "operation $index ambiguous property ${operation.name} on ${node.concept.qualifiedName}: " +
                                    resolution.properties.joinToString { it.name },
                        )
                    }
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
                    node.delete()
                    mutated = true
                }

                is EditOperation.DeleteChild -> {
                    val child = locateChild(node, operation.role, operation.position)
                        ?: fail(
                            MpsErrorCode.NODE_NOT_FOUND,
                            "operation $index child not found: role ${operation.role}, position ${operation.position}"
                        )
                    child.delete()
                    mutated = true
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
        position: ChildPosition
    ): SNode? {
        val children = sequence {
            var child = node.firstChild
            while (child != null) {
                yield(child)
                child = child.nextSibling
            }
        }
        val childrenInRole = children.filter { it.containmentLink?.name == role }

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
