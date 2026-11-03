package com.specificlanguages.mops.daemon

import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

// Rows shown when a model segment misses and the module's models are listed to help correct it.
private const val MISSING_MODEL_LISTING_LIMIT = 20

/** What a **Navigation Target** resolved to: a module, a model, a root node, or a nested node. */
internal sealed interface NavigationTarget {
    data class Module(val module: SModule) : NavigationTarget
    data class Model(val model: SModel) : NavigationTarget
    data class RootNode(val node: SNode) : NavigationTarget
    data class Node(val node: SNode) : NavigationTarget
}

internal sealed interface NavigationTargetResolution {
    data class Found(val target: NavigationTarget) : NavigationTargetResolution

    /** More than one object matched; [message] lists one typed, serialized candidate per line. */
    data class Ambiguous(val message: String) : NavigationTargetResolution

    /** Nothing matched; [detail], when present, helps the caller correct the path. */
    data class Missing(val detail: String? = null) : NavigationTargetResolution {
        fun detailLine(): String = detail?.let { "\n$it" }.orEmpty()
    }
}

/**
 * Resolves a **Navigation Target**, the path grammar of ADR-0002, against an **MPS Project**, and looks up the
 * serialized module, model, and node references the grammar accepts.
 *
 * Every function must be called inside a model read action.
 */
internal class NavigationTargetResolver(
    private val project: MPSProject,
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    /** Resolves a non-empty list of path segments. An empty list resolves to [NavigationTargetResolution.Missing]. */
    fun resolve(segments: List<String>): NavigationTargetResolution {
        if (segments.isEmpty()) {
            return NavigationTargetResolution.Missing()
        }

        if (segments.size == 1) {
            return resolveSingleSegment(segments.single())
        }

        resolveModelReference(segments[0])?.let {
            return resolveModelPath(it, segments.drop(1))
        }

        val module = when (val resolution = resolveProjectModule(segments[0])) {
            is NavigationTargetResolution.Found -> (resolution.target as NavigationTarget.Module).module
            is NavigationTargetResolution.Ambiguous, is NavigationTargetResolution.Missing -> return resolution
        }
        val modelName = modelName(module, segments[1])
        val models = module.models
            .filter { it.name.value == modelName }
            .toList()
        val model = when (models.size) {
            0 -> return missingModelInModule(module, modelName)
            1 -> models.single()
            else -> return ambiguousModelTarget(modelName, models)
        }

        return resolveModelPath(model, segments.drop(2))
    }

    /** Project modules (generators included) whose name or serialized reference equals [target]. */
    fun projectModulesMatching(target: String): List<SModule> =
        project.projectModulesWithGenerators.filter {
            it.moduleName == target || persistence.asString(it.moduleReference) == target
        }

    fun resolveNodeReference(target: String): SNode? =
        runCatching {
            persistence.createNodeReference(normalizeNodeReferenceSpelling(persistence, target)).resolve(project.repository)
        }.getOrNull()

    // A bare model name parses into a reference whose id is derived from the name. MPS 2024.1 resolves such an id
    // inexactly, to any model of that name; later versions resolve it only to a model with that exact id. Requiring the
    // ids to match gives the later behavior on every version, so a bare name is never taken as a model reference.
    fun resolveModelReference(target: String): SModel? =
        runCatching {
            val reference = persistence.createModelReference(target)
            reference.resolve(project.repository)?.takeIf { it.reference.modelId == reference.modelId }
        }.getOrNull()

    fun resolveModuleReference(target: String): SModule? =
        runCatching {
            persistence.createModuleReference(target).resolve(project.repository)
        }.getOrNull()

    private fun resolveModelPath(model: SModel, segments: List<String>): NavigationTargetResolution {
        if (segments.isEmpty()) {
            return NavigationTargetResolution.Found(NavigationTarget.Model(model))
        }
        val rootSegmentId = parseNodeIdOrNull(persistence, segments[0])
        val rootNodes = model.rootNodes
            .filter { nodeMatches(it, segments[0], rootSegmentId) }
            .toList()
        val rootNode = when (rootNodes.size) {
            0 -> return NavigationTargetResolution.Missing()
            1 -> rootNodes.single()
            else -> return ambiguousRootNodeTarget(segments[0], rootNodes)
        }

        if (segments.size == 1) {
            return NavigationTargetResolution.Found(NavigationTarget.RootNode(rootNode))
        }

        var node = rootNode
        for (segment in segments.drop(1)) {
            val segmentId = parseNodeIdOrNull(persistence, segment)
            val childNodes = node.children
                .filter { nodeMatches(it, segment, segmentId) }
                .toList()
            node = when (childNodes.size) {
                0 -> return NavigationTargetResolution.Missing()
                1 -> childNodes.single()
                else -> return ambiguousChildNodeTarget(segment, childNodes)
            }
        }
        return NavigationTargetResolution.Found(NavigationTarget.Node(node))
    }

    // A single navigation segment is resolved by counting every interpretation that actually matches — a serialized node
    // reference, a serialized model reference, a project model by name, or a project module by name or reference — rather
    // than by the segment's shape. A unique match wins; more than one (a module and a model sharing a name, or two
    // same-named models) is reported ambiguous with one typed, serialized candidate per match, so the caller can retry
    // with a reference. Serialized references and unique module names are unaffected: those forms match a single kind.
    private fun resolveSingleSegment(segment: String): NavigationTargetResolution {
        val candidates = LinkedHashMap<String, NavigationTarget>()

        resolveNodeReference(segment)?.let {
            candidates["node:${persistence.asString(it.reference)}"] = targetForNode(it)
        }
        resolveModelReference(segment)?.let {
            candidates["model:${persistence.asString(it.reference)}"] = NavigationTarget.Model(it)
        }
        matchingProjectModels(segment).forEach {
            candidates["model:${persistence.asString(it.reference)}"] = NavigationTarget.Model(it)
        }
        projectModulesMatching(segment).forEach {
            candidates["module:${persistence.asString(it.moduleReference)}"] = NavigationTarget.Module(it)
        }

        val targets = candidates.values.toList()
        return when (targets.size) {
            0 -> NavigationTargetResolution.Missing()
            1 -> NavigationTargetResolution.Found(targets.single())
            else -> ambiguousSingleSegment(segment, targets)
        }
    }

    private fun matchingProjectModels(segment: String): List<SModel> =
        project.projectModulesWithGenerators
            .asSequence()
            .flatMap { it.models.asSequence() }
            .filter { it.name.value == segment }
            .distinctBy { persistence.asString(it.reference) }
            .toList()

    private fun resolveProjectModule(target: String): NavigationTargetResolution {
        val modules = projectModulesMatching(target)
        return when (modules.size) {
            0 -> NavigationTargetResolution.Missing()
            1 -> NavigationTargetResolution.Found(NavigationTarget.Module(modules.single()))
            else -> ambiguousModuleTarget(target, modules)
        }
    }

    // The model segment after a module: a bare `.` names the model whose name equals the module name, a `.suffix`
    // segment appends to the module name, and any other segment is a full model name.
    private fun modelName(module: SModule, segment: String): String {
        val moduleName = module.moduleName.orEmpty()
        return when {
            segment == "." -> moduleName
            segment.startsWith(".") -> moduleName + segment
            else -> segment
        }
    }

    // A model segment that matches no model of an already resolved module lists that module's models, so the caller
    // can correct the segment without listing the module first. The listing is capped so a module with many models
    // still yields bounded output.
    private fun missingModelInModule(module: SModule, modelName: String): NavigationTargetResolution.Missing {
        val moduleName = module.moduleName.orEmpty()
        val models = module.models.toList()
        if (models.isEmpty()) {
            return NavigationTargetResolution.Missing("module $moduleName has no models")
        }
        val rows = models.take(MISSING_MODEL_LISTING_LIMIT).map(::modelRow)
        val omitted = models.size - rows.size
        val listing = rows + if (omitted > 0) listOf("$omitted more models not shown (module has ${models.size})") else emptyList()
        return NavigationTargetResolution.Missing(
            "module $moduleName has no model $modelName; its models:\n" + listing.joinToString("\n"),
        )
    }

    private fun ambiguousSingleSegment(segment: String, targets: List<NavigationTarget>): NavigationTargetResolution.Ambiguous =
        NavigationTargetResolution.Ambiguous(
            "ambiguous target $segment:\n" + targets.joinToString("\n", transform = ::candidateRow),
        )

    // One candidate line of a mixed-kind single-segment ambiguity, using the same per-kind row format each single-kind
    // ambiguity below emits, so the "ambiguous …" listing reads the same however the candidates were counted.
    private fun candidateRow(target: NavigationTarget): String =
        when (target) {
            is NavigationTarget.Module -> moduleRow(target.module)
            is NavigationTarget.Model -> modelRow(target.model)
            is NavigationTarget.RootNode -> rootNodeRow(target.node)
            is NavigationTarget.Node -> childNodeRow(target.node)
        }

    private fun moduleRow(module: SModule): String =
        "module\t${module.moduleName}\t${persistence.asString(module.moduleReference)}"

    private fun modelRow(model: SModel): String =
        "model\t${model.name.value}\t${persistence.asString(model.reference)}"

    private fun rootNodeRow(node: SNode): String =
        "root\t${nodeName(node) ?: "<unnamed>"}\t${persistence.asString(node.nodeId)}\t${persistence.asString(node.reference)}"

    private fun childNodeRow(node: SNode): String =
        "node\t${node.containmentLink?.role.orEmpty()}\t${nodeName(node) ?: "<unnamed>"}\t" +
            "${persistence.asString(node.nodeId)}\t${persistence.asString(node.reference)}"

    private fun ambiguousModuleTarget(target: String, modules: List<SModule>): NavigationTargetResolution.Ambiguous =
        ambiguousTarget("module", target, modules, ::moduleRow)

    private fun ambiguousModelTarget(modelName: String, models: List<SModel>): NavigationTargetResolution.Ambiguous =
        ambiguousTarget("model", modelName, models, ::modelRow)

    private fun ambiguousRootNodeTarget(target: String, nodes: List<SNode>): NavigationTargetResolution.Ambiguous =
        ambiguousTarget("root node", target, nodes, ::rootNodeRow)

    private fun ambiguousChildNodeTarget(target: String, nodes: List<SNode>): NavigationTargetResolution.Ambiguous =
        ambiguousTarget("child node", target, nodes, ::childNodeRow)

    private fun <T> ambiguousTarget(
        kind: String,
        target: String,
        items: List<T>,
        row: (T) -> String,
    ): NavigationTargetResolution.Ambiguous =
        NavigationTargetResolution.Ambiguous(
            "ambiguous $kind target $target:\n" + items.joinToString("\n", transform = row),
        )

    private fun nodeMatches(node: SNode, name: String, nodeId: SNodeId?): Boolean =
        nodeName(node) == name || node.nodeId == nodeId

    private fun targetForNode(node: SNode): NavigationTarget =
        if (node.parent == null) NavigationTarget.RootNode(node) else NavigationTarget.Node(node)
}
