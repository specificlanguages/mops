package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListSummaryGroupJson
import com.specificlanguages.mops.protocol.MpsListSummaryJson
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.Project
import jetbrains.mps.project.Solution
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.SNodeUtil
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

class MpsListExporter(
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun exportProject(project: Project, depth: Int, limit: Int, summary: Boolean): MpsListEntryJson {
        val modules = project.projectModulesWithGenerators.sortedBy { it.moduleName }
        if (summary) {
            return MpsListEntryJson(type = "project", name = project.name, summary = moduleKindSummary(modules))
        }
        val level = childLevel(depth, limit, modules) { module, childDepth -> moduleEntry(module, childDepth, limit) }
        return MpsListEntryJson(
            type = "project",
            name = project.name,
            children = level.children,
            childTotal = level.total,
        )
    }

    fun exportRepository(repository: SRepository, depth: Int, limit: Int, summary: Boolean): MpsListEntryJson {
        val modules = repository.modules.sortedBy { it.moduleName }
        if (summary) {
            return MpsListEntryJson(type = "repository", name = "/", summary = moduleKindSummary(modules))
        }
        val level = childLevel(depth, limit, modules) { module, childDepth -> moduleEntry(module, childDepth, limit) }
        return MpsListEntryJson(
            type = "repository",
            name = "/",
            children = level.children,
            childTotal = level.total,
        )
    }

    fun exportModule(module: SModule, depth: Int, limit: Int, summary: Boolean): MpsListEntryJson {
        if (summary) {
            return MpsListEntryJson(
                type = "module",
                name = module.moduleName,
                moduleKind = module.moduleKind(),
                reference = persistence.asString(module.moduleReference),
                summary = modelSummary(module),
            )
        }
        return moduleEntry(module, depth, limit)
    }

    fun exportModel(model: SModel, depth: Int, limit: Int, summary: Boolean): MpsListEntryJson {
        if (summary) {
            return MpsListEntryJson(
                type = "model",
                name = model.name.value,
                reference = persistence.asString(model.reference),
                summary = conceptSummary(model.rootNodes.toList()),
            )
        }
        return modelEntry(model, depth, limit)
    }

    fun exportRoot(node: SNode, depth: Int, limit: Int, summary: Boolean, role: String?): MpsListEntryJson =
        nodeTargetEntry(node, type = "root", depth = depth, limit = limit, summary = summary, role = role)

    fun exportNode(node: SNode, depth: Int, limit: Int, summary: Boolean, role: String?): MpsListEntryJson =
        nodeTargetEntry(node, type = "node", depth = depth, limit = limit, summary = summary, role = role)

    // Builds the entry for a node addressed directly as the list target. Unlike the recursive [nodeEntry], this level
    // applies --summary (children counted per Role) and --role (children restricted to one Containment Role); both
    // shape only this level, so descent below it uses the plain recursive form.
    private fun nodeTargetEntry(
        node: SNode,
        type: String,
        depth: Int,
        limit: Int,
        summary: Boolean,
        role: String?,
    ): MpsListEntryJson {
        val children = node.children.toList().let { all ->
            if (role != null) all.filter { it.containmentLink?.role == role } else all
        }
        if (summary) {
            return nodeHeader(node, type).copy(summary = roleSummary(children))
        }
        val level = childLevel(depth, limit, children) { child, childDepth -> childEntry(child, childDepth, limit) }
        return nodeHeader(node, type).copy(children = level.children, childTotal = level.total)
    }

    private fun moduleEntry(module: SModule, depth: Int, limit: Int): MpsListEntryJson {
        val level = childLevel(depth, limit, module.models.sortedBy { it.name.value }) { model, childDepth ->
            modelEntry(model, childDepth, limit)
        }
        return MpsListEntryJson(
            type = "module",
            name = module.moduleName,
            moduleKind = module.moduleKind(),
            reference = persistence.asString(module.moduleReference),
            children = level.children,
            childTotal = level.total,
        )
    }

    private fun modelEntry(model: SModel, depth: Int, limit: Int): MpsListEntryJson {
        val roots = model.rootNodes
            .sortedWith(compareBy<SNode> { nodeName(it) == null }.thenBy { nodeName(it) ?: "" })
        val level = childLevel(depth, limit, roots) { root, childDepth -> rootEntry(root, childDepth, limit) }
        return MpsListEntryJson(
            type = "model",
            name = model.name.value,
            reference = persistence.asString(model.reference),
            children = level.children,
            childTotal = level.total,
        )
    }

    private fun rootEntry(node: SNode, depth: Int, limit: Int): MpsListEntryJson {
        val level = childLevel(depth, limit, node.children.toList()) { child, childDepth ->
            childEntry(child, childDepth, limit)
        }
        return nodeHeader(node, type = "root").copy(children = level.children, childTotal = level.total)
    }

    private fun childEntry(node: SNode, depth: Int, limit: Int): MpsListEntryJson {
        val level = childLevel(depth, limit, node.children.toList()) { child, childDepth ->
            childEntry(child, childDepth, limit)
        }
        return nodeHeader(node, type = "node")
            .copy(role = node.containmentLink?.role, children = level.children, childTotal = level.total)
    }

    private fun nodeHeader(node: SNode, type: String): MpsListEntryJson =
        MpsListEntryJson(
            type = type,
            name = nodeName(node),
            concept = node.concept.qualifiedName,
            conceptValid = node.concept.isValid,
            id = persistence.asString(node.nodeId),
            reference = persistence.asString(node.reference),
        )

    // Bounds one level of children: nothing below the requested depth, otherwise the first [limit] entries (all when
    // [limit] is 0), recording the untruncated total only when some were dropped.
    private fun <T> childLevel(
        depth: Int,
        limit: Int,
        source: List<T>,
        entry: (T, childDepth: Int) -> MpsListEntryJson,
    ): Level {
        if (depth <= 0) {
            return Level(children = null, total = null)
        }
        val shown = if (limit > 0) source.take(limit) else source
        val truncated = shown.size < source.size
        return Level(
            children = shown.map { entry(it, depth - 1) },
            total = if (truncated) source.size else null,
        )
    }

    private class Level(val children: List<MpsListEntryJson>?, val total: Int?)

    // --- Summaries -------------------------------------------------------------------------------------------------

    private fun moduleKindSummary(modules: List<SModule>): MpsListSummaryJson =
        MpsListSummaryJson(
            by = "module-kind",
            groups = countGroups(modules) { it.moduleKind() },
        )

    private fun modelSummary(module: SModule): MpsListSummaryJson =
        MpsListSummaryJson(
            by = "model",
            groups = module.models
                .map { MpsListSummaryGroupJson(key = it.name.value, count = it.rootNodes.count()) }
                .sortedWith(compareByDescending<MpsListSummaryGroupJson> { it.count }.thenBy { it.key }),
        )

    private fun conceptSummary(roots: List<SNode>): MpsListSummaryJson =
        MpsListSummaryJson(
            by = "concept",
            groups = countGroups(roots) { it.concept.qualifiedName },
        )

    private fun roleSummary(children: List<SNode>): MpsListSummaryJson =
        MpsListSummaryJson(
            by = "role",
            groups = children
                .groupBy { it.containmentLink?.role ?: "<none>" }
                .map { (role, nodes) ->
                    MpsListSummaryGroupJson(
                        key = role,
                        count = nodes.size,
                        concepts = dominantConcepts(nodes),
                    )
                }
                .sortedWith(compareByDescending<MpsListSummaryGroupJson> { it.count }.thenBy { it.key }),
        )

    // Distinct concepts of the nodes filling a role, most frequent first, ties broken by name so the order is stable.
    private fun dominantConcepts(nodes: List<SNode>): List<String> =
        nodes
            .groupingBy { it.concept.qualifiedName }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    private fun <T> countGroups(items: List<T>, key: (T) -> String): List<MpsListSummaryGroupJson> =
        items
            .groupingBy(key)
            .eachCount()
            .map { (key, count) -> MpsListSummaryGroupJson(key = key, count = count) }
            .sortedWith(compareByDescending<MpsListSummaryGroupJson> { it.count }.thenBy { it.key })

    private fun nodeName(node: SNode): String? =
        SNodeAccessUtil.getPropertyValue(node, SNodeUtil.property_INamedConcept_name) as String?

    private fun SModule.moduleKind(): String =
        when (this) {
            is Language -> "language"
            is Generator -> "generator"
            is Solution -> "solution"
            is DevKit -> "devkit"
            else -> "other"
        }
}
