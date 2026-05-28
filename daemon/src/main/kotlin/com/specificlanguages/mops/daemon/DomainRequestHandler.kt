package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRequest
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ModelGetNodeRequest
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.ModelResaveRequest
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.MpsListRequest
import com.specificlanguages.mops.protocol.MpsListResponse
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.SNodeUtil
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SaveOptions
import org.jetbrains.mps.openapi.model.SaveResult
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.io.path.pathString

class DomainRequestHandler(val logger: DaemonLogger, val workspacePath: Path) {
    private val modelNodeResolver = ModelNodeResolver(logger)
    private val mpsListExporter = MpsListExporter()
    private val persistence = PersistenceFacade.getInstance()

    fun handleDomainRequest(project: Project, request: DaemonRequest): DaemonResponse {
        return when (request) {
            is ModelGetNodeRequest -> getNode(project, request)
            is ModelResaveRequest -> resaveModel(project, request)
            is MpsListRequest -> list(project, request)
            else -> errorResponse("UNSUPPORTED_REQUEST", "unsupported request type: ${request.type}")
        }
    }

    private fun list(project: Project, request: MpsListRequest): DaemonResponse {
        return project.modelAccess.computeReadAction {
            val target = request.target
            val root = when (target) {
                null -> mpsListExporter.exportProject(project, request.depth)
                listOf("/") -> mpsListExporter.exportRepository(project.repository, request.depth)
                else -> {
                    val root = resolveListTarget(project, target)
                        ?: return@computeReadAction errorResponse(
                            code = "TARGET_NOT_FOUND",
                            message = "target not found: ${target.joinToString(" ")}",
                        )
                    when (root) {
                        is ListTarget.Module -> mpsListExporter.exportModule(root.module, request.depth)
                        is ListTarget.Model -> mpsListExporter.exportModel(root.model, request.depth)
                        is ListTarget.RootNode -> mpsListExporter.exportRoot(root.node, request.depth)
                    }
                }
            }
            MpsListResponse(root = root)
        }
    }

    private fun resolveListTarget(project: Project, target: List<String>): ListTarget? {
        if (target.size == 1) {
            resolveModelReference(project, target.single())?.let { return ListTarget.Model(it) }
        }

        if (target.size == 1) {
            return findProjectModule(project, target.single())?.let(ListTarget::Module)
        }
        if (target.size !in 2..3) {
            return null
        }

        val moduleName = target[0]
        val modelName = target[1]
        val module = findProjectModule(project, moduleName) ?: return null
        val model = module.models
            .singleOrNull { it.name.value == modelName }
            ?: return null

        if (target.size == 2) {
            return ListTarget.Model(model)
        }

        return model.rootNodes
            .singleOrNull { nodeName(it) == target[2] || persistence.asString(it.nodeId) == target[2] }
            ?.let(ListTarget::RootNode)
    }

    private fun resolveModelReference(project: Project, target: String): SModel? =
        runCatching {
            persistence.createModelReference(target).resolve(project.repository)
        }.getOrNull()

    private fun findProjectModule(project: Project, target: String): SModule? =
        project.projectModulesWithGenerators.singleOrNull { it.moduleName == target }

    private fun nodeName(node: SNode): String? =
        SNodeAccessUtil.getPropertyValue(node, SNodeUtil.property_INamedConcept_name) as String?

    private sealed interface ListTarget {
        data class Module(val module: SModule) : ListTarget
        data class Model(val model: SModel) : ListTarget
        data class RootNode(val node: SNode) : ListTarget
    }

    private fun getNode(project: Project, request: ModelGetNodeRequest): DaemonResponse {
        return try {
            project.modelAccess.computeReadAction {
                val node = modelNodeResolver.findNode(project, request.target)
                    ?: return@computeReadAction errorResponse(
                        code = "NODE_NOT_FOUND",
                        message = "node not found",
                    )
                ModelGetNodeResponse(node = JsonNodeExporter().export(node))
            }
        } catch (exception: Exception) {
            errorResponse(
                code = "GET_NODE_FAILED",
                message = exception.message ?: exception.javaClass.name,
            )
        }
    }

    private fun resaveModel(project: Project, request: ModelResaveRequest): DaemonResponse {
        val modelTarget = request.modelTarget
        if (modelTarget.isNullOrBlank()) {
            return errorResponse("INVALID_REQUEST", "modelTarget is required")
        }

        val future = CompletableFuture<DaemonResponse>()

        project.modelAccess.executeCommandInEDT {
            try {
                val response = project.modelAccess.computeWriteAction {
                    val model = modelNodeResolver.findModel(project, modelTarget)
                        ?: return@computeWriteAction errorResponse(
                            code = "MODEL_NOT_FOUND",
                            message = "model not found: $modelTarget",
                        )
                    if (model.isReadOnly || model !is EditableSModel) {
                        return@computeWriteAction errorResponse(
                            code = "MODEL_READ_ONLY",
                            message = "model is not editable: ${model.name.longName}",
                        )
                    }

                    model.load()

                    val result = model.save(SaveOptions.FORCE_SAVE_WITH_RESOLVE_INFO).toCompletableFuture().join()

                    if (result != SaveResult.SAVED_TO_DATA_SOURCE && result != SaveResult.NOT_CHANGED) {
                        return@computeWriteAction errorResponse(
                            code = "SAVE_FAILED",
                            message = "model save failed for ${model.name.longName}: $result",
                        )
                    }

                    ModelResaveResponse(modelTarget = modelTarget)
                }
                future.complete(response)
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }

        return try {
            future.get()
        } catch (exception: Exception) {
            val cause = if (exception is ExecutionException) exception.cause else exception
            errorResponse(
                code = "SAVE_FAILED",
                message = cause?.message
                    ?: cause?.javaClass?.name
                    ?: exception.message
                    ?: exception.javaClass.name,
            )
        }
    }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)
}
