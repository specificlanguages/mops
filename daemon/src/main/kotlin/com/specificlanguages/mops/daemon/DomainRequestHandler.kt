package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRequest
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ModelGetNodeRequest
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.ModelResaveRequest
import com.specificlanguages.mops.protocol.ModelResaveResponse
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.JavaFriendlyBase64
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.model.SaveOptions
import org.jetbrains.mps.openapi.model.SaveResult
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.io.path.pathString

class DomainRequestHandler(val logger: DaemonLogger, val workspacePath: Path) {

    fun handleDomainRequest(project: Project, request: DaemonRequest): DaemonResponse {
        return when (request) {
            is ModelGetNodeRequest -> getNode(project, request)
            is ModelResaveRequest -> resaveModel(project, request)
            else -> errorResponse("UNSUPPORTED_REQUEST", "unsupported request type: ${request.type}")
        }
    }

    private fun getNode(project: Project, request: ModelGetNodeRequest): DaemonResponse {
        return try {
            project.modelAccess.computeReadAction {
                val node = resolveNode(project, request)
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

    private fun resolveNode(project: Project, request: ModelGetNodeRequest): SNode? {
        val nodeReference = request.nodeReference
        if (!nodeReference.isNullOrBlank()) {
            return PersistenceFacade.getInstance()
                .createNodeReference(nodeReference)
                .resolve(project.repository)
        }

        val modelTarget = request.modelTarget
        if (modelTarget.isNullOrBlank()) {
            throw IllegalArgumentException("modelTarget is required when nodeReference is not provided")
        }
        val nodeId = request.nodeId
        if (nodeId.isNullOrBlank()) {
            throw IllegalArgumentException("nodeId is required when nodeReference is not provided")
        }

        val model = findSingleModel(project, modelTarget)
            ?: throw IllegalArgumentException("model not found: $modelTarget")
        model.load()
        return model.getNode(createNodeId(nodeId))
    }

    private fun createNodeId(nodeId: String): SNodeId {
        val persistence = PersistenceFacade.getInstance()
        val parsed = persistence.createNodeId(nodeId)
        if (parsed != null) {
            return parsed
        }

        val decoded = JavaFriendlyBase64().parseLong(nodeId)
        return requireNotNull(persistence.createNodeId(java.lang.Long.toUnsignedString(decoded))) {
            "could not parse nodeId: $nodeId"
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
                    val model = findModel(project, modelTarget)
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

    private fun findModel(project: Project, modelTarget: String): SModel? {
        val candidates = matchingModels(project, modelTarget)
        val model = candidates.firstOrNull()
        if (model == null) {
            logMissingModelTarget(project, modelTarget)
        }
        return model
    }

    private fun findSingleModel(project: Project, modelTarget: String): SModel? {
        val candidates = matchingModels(project, modelTarget)
        if (candidates.size > 1) {
            throw IllegalArgumentException(
                "ambiguous model target $modelTarget: " +
                    candidates.joinToString { "${it.name.longName} [${it.filePath()}]" },
            )
        }

        val model = candidates.firstOrNull()
        if (model == null) {
            logMissingModelTarget(project, modelTarget)
        }
        return model
    }

    private fun matchingModels(project: Project, modelTarget: String): List<SModel> {
        val targetPath = targetPath(modelTarget)
        return modelCandidates(project)
            .filter { model ->
                model.name.longName == modelTarget ||
                    model.name.value == modelTarget ||
                    PersistenceFacade.getInstance().asString(model.reference) == modelTarget ||
                    targetPath != null && model.filePath() == targetPath
            }
            .toList()
    }

    private fun logMissingModelTarget(project: Project, modelTarget: String) {
        val candidates = modelCandidates(project).toList()
        logger.log(
            "model target $modelTarget not found among ${candidates.size} models: " +
                candidates.take(20).joinToString { "${it.name.longName} [${it.filePath()}]" },
        )
    }

    private fun modelCandidates(project: Project): Sequence<SModel> =
        (
            project.projectModulesWithGenerators.asSequence().flatMap { it.models.asSequence() } +
                project.repository.modules.asSequence().flatMap { it.models.asSequence() }
            ).distinctBy { it.reference }

    private fun targetPath(modelTarget: String): Path? =
        runCatching {
            Path.of(modelTarget).let { path ->
                if (Files.exists(path)) {
                    path.toRealPath()
                } else {
                    path.toAbsolutePath().normalize()
                }
            }
        }.getOrNull()

    private fun SModel.filePath(): Path? {
        val dataSource = source
        if (dataSource is FileDataSource) {
            return runCatching { Path.of(dataSource.file.toRealPath()).toAbsolutePath().normalize() }.getOrNull()
        }
        return runCatching { Path.of(dataSource.location).toAbsolutePath().normalize() }.getOrNull()
    }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)
}
