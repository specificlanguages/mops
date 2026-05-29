package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsListEntryJson
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
    fun exportProject(project: Project, depth: Int): MpsListEntryJson =
        MpsListEntryJson(
            type = "project",
            name = project.name,
            children = childEntries(depth) { childDepth ->
                project.projectModulesWithGenerators
                    .sortedBy { it.moduleName }
                    .map { moduleEntry(it, childDepth) }
            },
        )

    fun exportRepository(repository: SRepository, depth: Int): MpsListEntryJson =
        MpsListEntryJson(
            type = "repository",
            name = "/",
            children = childEntries(depth) { childDepth ->
                repository.modules
                    .asSequence()
                    .sortedBy { it.moduleName }
                    .map { moduleEntry(it, childDepth) }
                    .toList()
            },
        )

    fun exportModule(module: SModule, depth: Int): MpsListEntryJson =
        moduleEntry(module, depth)

    fun exportModel(model: SModel, depth: Int): MpsListEntryJson =
        modelEntry(model, depth)

    fun exportRoot(node: SNode, depth: Int): MpsListEntryJson =
        rootEntry(node, depth)

    fun exportNode(node: SNode, depth: Int): MpsListEntryJson =
        nodeEntry(node, role = null, depth = depth)

    private fun moduleEntry(module: SModule, depth: Int): MpsListEntryJson =
        MpsListEntryJson(
            type = "module",
            name = module.moduleName,
            moduleKind = module.moduleKind(),
            reference = persistence.asString(module.moduleReference),
            children = childEntries(depth) { childDepth ->
                module.models
                    .asSequence()
                    .sortedBy { it.name.value }
                    .map { modelEntry(it, childDepth) }
                    .toList()
            },
        )

    private fun modelEntry(model: SModel, depth: Int): MpsListEntryJson =
        MpsListEntryJson(
            type = "model",
            name = model.name.value,
            reference = persistence.asString(model.reference),
            children = childEntries(depth) { childDepth ->
                model.rootNodes
                    .asSequence()
                    .sortedWith(compareBy<SNode> { nodeName(it) == null }.thenBy { nodeName(it) ?: "" })
                    .map { rootEntry(it, childDepth) }
                    .toList()
            },
        )

    private fun rootEntry(node: SNode, depth: Int): MpsListEntryJson =
        MpsListEntryJson(
            type = "root",
            name = nodeName(node),
            concept = node.concept.qualifiedName,
            id = persistence.asString(node.nodeId),
            reference = persistence.asString(node.reference),
            children = containmentEntries(node, depth),
        )

    private fun childEntry(node: SNode, depth: Int): MpsListEntryJson =
        nodeEntry(node, role = node.containmentLink?.role, depth = depth)

    private fun nodeEntry(node: SNode, role: String?, depth: Int): MpsListEntryJson =
        MpsListEntryJson(
            type = "node",
            role = role,
            name = nodeName(node),
            concept = node.concept.qualifiedName,
            id = persistence.asString(node.nodeId),
            reference = persistence.asString(node.reference),
            children = containmentEntries(node, depth),
        )

    private fun containmentEntries(node: SNode, depth: Int): List<MpsListEntryJson>? =
        childEntries(depth) { childDepth ->
            node.children
                .asSequence()
                .map { childEntry(it, childDepth) }
                .toList()
        }

    private fun childEntries(
        depth: Int,
        entries: (childDepth: Int) -> List<MpsListEntryJson>,
    ): List<MpsListEntryJson>? =
        if (depth > 0) {
            entries(depth - 1)
        } else {
            null
        }

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
