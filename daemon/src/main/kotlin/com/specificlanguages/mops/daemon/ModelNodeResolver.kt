package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModelGetNodeRequest
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.JavaFriendlyBase64
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Files
import java.nio.file.Path

class ModelNodeResolver(
    private val logger: DaemonLogger,
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun resolveGetNode(project: Project, request: ModelGetNodeRequest): SNode? {
        val nodeReference = request.nodeReference
        if (!nodeReference.isNullOrBlank()) {
            return persistence
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

    fun findModel(project: Project, modelTarget: String): SModel? {
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

    private fun createNodeId(nodeId: String): SNodeId {
        val parsed = persistence.createNodeId(nodeId)
        if (parsed != null) {
            return parsed
        }

        val decoded = JavaFriendlyBase64().parseLong(nodeId)
        return requireNotNull(persistence.createNodeId(java.lang.Long.toUnsignedString(decoded))) {
            "could not parse nodeId: $nodeId"
        }
    }

    private fun matchingModels(project: Project, modelTarget: String): List<SModel> {
        val targetPath = targetPath(modelTarget)
        return modelCandidates(project)
            .filter { model ->
                model.name.longName == modelTarget ||
                    model.name.value == modelTarget ||
                    persistence.asString(model.reference) == modelTarget ||
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
}
