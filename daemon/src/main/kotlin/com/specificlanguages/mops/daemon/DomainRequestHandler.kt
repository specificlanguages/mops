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
import jetbrains.mps.smodel.persistence.def.v9.IdEncoder
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SaveOptions
import org.jetbrains.mps.openapi.model.SaveResult
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import org.jetbrains.mps.openapi.model.SNodeId
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
                    val root = when (val resolution = resolveListTarget(project, target)) {
                        is ListTargetResolution.Found -> resolution.target
                        is ListTargetResolution.Ambiguous -> return@computeReadAction errorResponse(
                            code = "AMBIGUOUS_TARGET",
                            message = resolution.message,
                        )
                        ListTargetResolution.Missing -> return@computeReadAction errorResponse(
                            code = "TARGET_NOT_FOUND",
                            message = "target not found: ${target.joinToString(" ")}",
                        )
                    }
                    when (root) {
                        is ListTarget.Module -> mpsListExporter.exportModule(root.module, request.depth)
                        is ListTarget.Model -> mpsListExporter.exportModel(root.model, request.depth)
                        is ListTarget.RootNode -> mpsListExporter.exportRoot(root.node, request.depth)
                        is ListTarget.Node -> mpsListExporter.exportNode(root.node, request.depth)
                    }
                }
            }
            MpsListResponse(root = root)
        }
    }

    private fun resolveListTarget(project: Project, target: List<String>): ListTargetResolution {
        if (target.size == 1) {
            resolveNodeReference(project, target.single())?.let {
                return ListTargetResolution.Found(listTargetForNode(it))
            }
            resolveModelReference(project, target.single())?.let {
                return ListTargetResolution.Found(ListTarget.Model(it))
            }
        }

        if (target.size == 1) {
            return findProjectModule(project, target.single())
                ?.let { ListTargetResolution.Found(ListTarget.Module(it)) }
                ?: ListTargetResolution.Missing
        }
        if (target.size < 2) {
            return ListTargetResolution.Missing
        }

        val module = findProjectModule(project, target[0]) ?: return ListTargetResolution.Missing
        val modelName = modelName(module, target[1])
        val models = module.models
            .filter { it.name.value == modelName }
            .toList()
        val model = when (models.size) {
            0 -> return ListTargetResolution.Missing
            1 -> models.single()
            else -> return ambiguousModelTarget(modelName, models)
        }

        if (target.size == 2) {
            return ListTargetResolution.Found(ListTarget.Model(model))
        }

        val rootNodes = model.rootNodes
            .filter { nodeMatches(it, target[2]) }
            .toList()
        val rootNode = when (rootNodes.size) {
            0 -> return ListTargetResolution.Missing
            1 -> rootNodes.single()
            else -> return ambiguousRootNodeTarget(target[2], rootNodes)
        }

        if (target.size == 3) {
            return ListTargetResolution.Found(ListTarget.RootNode(rootNode))
        }

        var node = rootNode
        for (segment in target.drop(3)) {
            node = node.children.singleOrNull { nodeMatches(it, segment) } ?: return ListTargetResolution.Missing
        }
        return ListTargetResolution.Found(ListTarget.Node(node))
    }

    private fun ambiguousModelTarget(modelName: String, models: List<SModel>): ListTargetResolution.Ambiguous =
        ListTargetResolution.Ambiguous(
            "ambiguous model target $modelName:\n" +
                models.joinToString("\n") {
                    "model\t${it.name.value}\t${persistence.asString(it.reference)}"
                },
        )

    private fun ambiguousRootNodeTarget(target: String, nodes: List<SNode>): ListTargetResolution.Ambiguous =
        ListTargetResolution.Ambiguous(
            "ambiguous root node target $target:\n" +
                nodes.joinToString("\n") {
                    "root\t${nodeName(it) ?: "<unnamed>"}\t" +
                        "${persistence.asString(it.nodeId)}\t${persistence.asString(it.reference)}"
                },
        )

    private fun resolveNodeReference(project: Project, target: String): SNode? =
        runCatching {
            persistence.createNodeReference(target).resolve(project.repository)
        }.getOrNull()

    private fun resolveModelReference(project: Project, target: String): SModel? =
        runCatching {
            persistence.createModelReference(target).resolve(project.repository)
        }.getOrNull()

    private fun findProjectModule(project: Project, target: String): SModule? =
        project.projectModulesWithGenerators.singleOrNull {
            it.moduleName == target || persistence.asString(it.moduleReference) == target
        }

    private fun modelName(module: SModule, segment: String): String =
        if (segment.startsWith(".") && segment.length > 1) {
            module.moduleName + segment
        } else {
            segment
        }

    private fun nodeName(node: SNode): String? =
        SNodeAccessUtil.getPropertyValue(node, SNodeUtil.property_INamedConcept_name) as String?

    private fun nodeMatches(node: SNode, segment: String): Boolean =
        nodeName(node) == segment || node.nodeId == parseNodeIdOrNull(segment)

    private fun parseNodeIdOrNull(nodeId: String): SNodeId? =
        runCatching {
            if (nodeId.all(Char::isDigit)) {
                persistence.createNodeId(nodeId)
            } else {
                IdEncoder().parseNodeId(nodeId)
            }
        }.getOrNull()

    private fun listTargetForNode(node: SNode): ListTarget =
        if (node.parent == null) {
            ListTarget.RootNode(node)
        } else {
            ListTarget.Node(node)
        }

    private sealed interface ListTarget {
        data class Module(val module: SModule) : ListTarget
        data class Model(val model: SModel) : ListTarget
        data class RootNode(val node: SNode) : ListTarget
        data class Node(val node: SNode) : ListTarget
    }

    private sealed interface ListTargetResolution {
        data class Found(val target: ListTarget) : ListTargetResolution
        data class Ambiguous(val message: String) : ListTargetResolution
        data object Missing : ListTargetResolution
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
