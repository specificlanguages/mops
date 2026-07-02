package com.specificlanguages.mops.daemon

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
    ): ModelEditOutcome {
        if (batch.operations.isEmpty()) {
            return ModelEditOutcome.Failure(
                code = "INVALID_REQUEST",
                message = "edit batch must contain at least one operation",
            )
        }

        val affectedModels = linkedSetOf<EditableSModel>()
        var mutated = false

        fun fail(code: String, message: String): ModelEditOutcome.Failure {
            if (mutated) {
                reload(affectedModels)
            }
            return ModelEditOutcome.Failure(code = code, message = message)
        }

        for ((index, operation) in batch.operations.withIndex()) {
            val target = when (operation) {
                is EditOperation.SetProperty -> operation.target
            }
            if (target is EditTarget.Alias) {
                return fail(
                    "UNSUPPORTED_TARGET",
                    "operation $index alias targets are not supported by this edit slice: ${target.alias}",
                )
            }
            val node = try {
                resolveTarget(project, target)
            } catch (exception: Exception) {
                return fail(
                    "TARGET_RESOLUTION_FAILED",
                    "operation $index target could not be resolved: ${exception.message ?: exception.javaClass.name}",
                )
            }
                ?: return fail("NODE_NOT_FOUND", "operation $index target node not found")
            val model = node.model
                ?: return fail("NODE_NOT_FOUND", "operation $index target node is detached from a model")
            val editableModel = writeScope.asEditable(model)
                ?: return fail(
                    "MODEL_READ_ONLY",
                    "operation $index target model is not editable: ${model.name.longName}",
                )
            if (editableModel !in affectedModels) {
                if (editableModel.isChanged) {
                    return fail(
                        "MODEL_CHANGED",
                        "operation $index target model has unsaved changes: ${editableModel.name.longName}",
                    )
                }
                affectedModels.add(editableModel)
            }

            when (operation) {
                is EditOperation.SetProperty -> {
                    val property = when (val resolution = resolveProperty(node, operation.name)) {
                        is PropertyResolution.Found -> resolution.property
                        PropertyResolution.Missing -> return fail(
                            "PROPERTY_NOT_FOUND",
                            "operation $index property not found: ${operation.name} on ${node.concept.qualifiedName}",
                        )
                        is PropertyResolution.Ambiguous -> return fail(
                            "AMBIGUOUS_PROPERTY",
                            "operation $index ambiguous property ${operation.name} on ${node.concept.qualifiedName}: " +
                                resolution.properties.joinToString { it.name },
                        )
                    }
                    try {
                        SNodeAccessUtil.setPropertyValue(node, property, operation.value)
                        mutated = true
                    } catch (exception: Exception) {
                        return fail(
                            "MODEL_EDIT_FAILED",
                            "operation $index failed: ${exception.message ?: exception.javaClass.name}",
                        )
                    }
                }
            }
        }

        return when (val saveOutcome = writeScope.saveWithResolveInfo(affectedModels)) {
            SaveOutcome.Saved -> ModelEditOutcome.Success(
                ModelEditResponse(created = emptyMap(), violations = emptyList()),
            )
            is SaveOutcome.SaveFailed -> {
                if (mutated) {
                    reload(affectedModels)
                }
                ModelEditOutcome.Failure(
                    code = "SAVE_FAILED",
                    message = "model save failed for ${saveOutcome.model.name.longName}: ${saveOutcome.result}",
                )
            }
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

sealed interface ModelEditOutcome {
    data class Success(val response: ModelEditResponse) : ModelEditOutcome
    data class Failure(val code: String, val message: String) : ModelEditOutcome
}

private sealed interface PropertyResolution {
    data class Found(val property: SProperty) : PropertyResolution
    data class Ambiguous(val properties: List<SProperty>) : PropertyResolution
    data object Missing : PropertyResolution
}
