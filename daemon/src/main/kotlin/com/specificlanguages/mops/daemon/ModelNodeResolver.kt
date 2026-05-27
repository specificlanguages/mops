package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.GetNodeTarget
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
    fun resolveGetNode(project: Project, target: GetNodeTarget): SNode? =
        when (target) {
            is GetNodeTarget.NodeReference -> persistence
                .createNodeReference(target.nodeReference)
                .resolve(project.repository)

            is GetNodeTarget.InModel -> {
                val model = findSingleModel(project, target.modelTarget)
                    ?: throw IllegalArgumentException("model not found: ${target.modelTarget}")
                model.load()
                model.getNode(createNodeId(target.nodeId))
            }
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
