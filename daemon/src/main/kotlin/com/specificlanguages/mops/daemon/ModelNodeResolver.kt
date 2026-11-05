package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.NodeTarget
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.project.Project
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Path

/** Describes the notations [ModelNodeResolver.findNode] accepts for a node target written as one string. */
internal const val NODE_TARGET_NOTATIONS =
    "<model>/<node ID> (model name or serialized model reference; decimal or encoded node ID) " +
        "or a serialized node reference"

class ModelNodeResolver(
    private val logger: DaemonLogger,
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun findNode(project: Project, target: NodeTarget): SNode? =
        when (target) {
            is NodeTarget.NodeReference -> findNode(project, target.nodeReference)

            is NodeTarget.InModel -> {
                val model = findSingleModel(project, target.modelTarget)
                    ?: throw MpsRequestException(
                        code = MpsErrorCode.MODEL_NOT_FOUND,
                        message = "model not found: ${target.modelTarget}",
                    )
                val nodeId = parseNodeIdOrNull(persistence, target.nodeId)
                    ?: throw MpsRequestException(
                        code = MpsErrorCode.INVALID_REQUEST,
                        message = "could not parse node ID: ${target.nodeId}",
                    )
                model.getNode(nodeId)
            }
        }

    /**
     * Resolves a node target written as one string: `<model>/<node ID>`, where `<model>` is a model target and the node
     * ID is in its decimal or encoded spelling, or a serialized node reference, whose node ID may also use either
     * spelling. Returns null when the target is well formed but names no node. Throws [IllegalArgumentException] when
     * the target is in neither notation, and [MpsRequestException] when the model target is ambiguous.
     */
    fun findNode(project: Project, target: String): SNode? {
        val slash = target.lastIndexOf('/')
        if (slash > 0) {
            val model = findSingleModel(project, target.substring(0, slash))
            val nodeId = parseNodeIdOrNull(persistence, target.substring(slash + 1))
            if (model != null && nodeId != null) return model.getNode(nodeId)
        }
        val reference = persistence.createNodeReference(normalizeNodeReferenceSpelling(persistence, target))
        // A bare model name parses into a reference whose model id is derived from the name. MPS 2024.1 resolves such an
        // id inexactly, to any model of that name; requiring the ids to match gives the later versions' behavior.
        return reference.resolve(project.repository)
            ?.takeIf { it.model?.reference?.modelId == reference.modelReference?.modelId }
    }

    /**
     * Resolves a model target to a single model, failing with [MpsErrorCode.AMBIGUOUS_TARGET] when more than one model
     * matches. Used where addressing a wrong model would be a mutation, so an ambiguous target must never silently pick
     * one.
     */
    fun findModelUnique(project: Project, modelTarget: String): SModel? = findSingleModel(project, modelTarget)

    private fun findSingleModel(project: Project, modelTarget: String): SModel? {
        val candidates = matchingModels(project, modelTarget)
        if (candidates.size > 1) {
            throw MpsRequestException(
                code = MpsErrorCode.AMBIGUOUS_TARGET,
                message = "ambiguous model target $modelTarget: " +
                    candidates.joinToString { "${it.name.longName} [${it.filePath()}]" },
            )
        }

        val model = candidates.firstOrNull()
        if (model == null) {
            logMissingModelTarget(project, modelTarget)
        }
        return model
    }

    private fun matchingModels(project: Project, modelTarget: String): List<SModel> =
        modelCandidates(project)
            .filter { model ->
                model.name.value == modelTarget ||
                    persistence.asString(model.reference) == modelTarget
            }
            .toList()

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

    private fun SModel.filePath(): Path? =
        source.runCatching {
            Path.of(if (this is FileDataSource) file.toRealPath() else location)
                .toAbsolutePath()
                .normalize()
        }.getOrNull()
}
