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

/**
 * Logic. Looks up the model or node that a model target or a **Node Target** names in an **MPS Project**.
 *
 * Hides: which models a model target matches, and what zero or several matches mean. A model reference matches by
 * model ID; a model name matches every model with that name, and more than one fails with
 * [MpsErrorCode.AMBIGUOUS_TARGET].
 *
 * Exception to **`logic-no-io`**: when [findModelUnique] finds no model, it writes a diagnostic log line that lists the
 * models it searched, and that line and the ambiguity message name each model's file, which reads the file system.
 * Building that list belongs with the search, and a caller would have to repeat the scan to log it.
 */
class ModelNodeResolver(
    private val logger: DaemonLogger,
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun findNode(project: Project, target: NodeTarget): SNode? =
        when (target) {
            is NodeTarget.SingleString -> findNode(project, target.text)

            is NodeTarget.InModel -> findNode(project, target.modelTarget, target.nodeId)
        }

    /**
     * Resolves a **Node Target** written as one string (see [parseNodeTarget]). Returns null when its model holds no
     * such node. Fails like a model target and node ID given apart: with [MpsErrorCode.INVALID_REQUEST] when the target
     * is malformed, [MpsErrorCode.MODEL_NOT_FOUND] when no model matches, and [MpsErrorCode.AMBIGUOUS_TARGET] when its
     * model name matches several.
     */
    fun findNode(project: Project, target: String): SNode? = resolve(project, parseNodeTarget(persistence, target))

    /**
     * Like [findNode] with one string, for a node target whose model target and node ID come apart: the two-token form of
     * a command, a `{model, nodeId}` edit target, or an inline reference target.
     */
    fun findNode(project: Project, model: String, nodeId: String): SNode? =
        resolve(project, parseNodeTarget(persistence, model, nodeId))

    private fun resolve(project: Project, target: ParsedNodeTarget): SNode? =
        when (target) {
            is ParsedNodeTarget.Reference -> {
                if (target.reference.modelReference?.resolve(project.repository) == null) throw modelNotFound(target.model)
                target.reference.resolve(project.repository)
            }

            // A model name names one model or fails as ambiguous, whether or not the other models hold the node.
            is ParsedNodeTarget.InNamedModel ->
                (findModelUnique(project, target.modelName) ?: throw modelNotFound(target.modelName)).getNode(target.nodeId)
        }

    private fun modelNotFound(modelTarget: String) =
        MpsRequestException(code = MpsErrorCode.MODEL_NOT_FOUND, message = "model not found: $modelTarget")

    /**
     * Resolves a model target to a single model, failing with [MpsErrorCode.AMBIGUOUS_TARGET] when more than one model
     * matches. Used where addressing a wrong model would be a mutation, so an ambiguous target must never silently pick
     * one.
     */
    fun findModelUnique(project: Project, modelTarget: String): SModel? {
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

    // A target that parses as a serialized model reference is that reference and resolves by model ID alone, so a
    // reference whose name part is out of date still finds its model. It never falls back to a name lookup. Any other
    // target is a model name.
    private fun matchingModels(project: Project, modelTarget: String): List<SModel> {
        val reference = parseModelReferenceOrNull(persistence, modelTarget)
        if (reference != null) {
            return listOfNotNull(reference.resolve(project.repository))
        }
        return modelCandidates(project).filter { it.name.value == modelTarget }.toList()
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

    private fun SModel.filePath(): Path? =
        source.runCatching {
            Path.of(if (this is FileDataSource) file.toRealPath() else location)
                .toAbsolutePath()
                .normalize()
        }.getOrNull()
}
