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
            children = if (depth > 0) {
                project.projectModulesWithGenerators
                    .sortedBy { it.moduleName }
                    .map { moduleEntry(it) }
            } else {
                null
            },
        )

    fun exportRepository(repository: SRepository, depth: Int): MpsListEntryJson =
        MpsListEntryJson(
            type = "repository",
            name = "/",
            children = if (depth > 0) {
                repository.modules
                    .asSequence()
                    .sortedBy { it.moduleName }
                    .map { moduleEntry(it) }
                    .toList()
            } else {
                null
            },
        )

    fun exportModule(module: SModule, depth: Int): MpsListEntryJson =
        moduleEntry(
            module,
            children = if (depth > 0) {
                module.models
                    .asSequence()
                    .sortedBy { it.name.value }
                    .map(::modelEntry)
                    .toList()
            } else {
                null
            },
        )

    fun exportModel(model: SModel, depth: Int): MpsListEntryJson =
        modelEntry(
            model,
            children = if (depth > 0) {
                model.rootNodes
                    .asSequence()
                    .sortedWith(compareBy<SNode> { nodeName(it) == null }.thenBy { nodeName(it) ?: "" })
                    .map(::rootEntry)
                    .toList()
            } else {
                null
            },
        )

    private fun moduleEntry(module: SModule, children: List<MpsListEntryJson>? = null): MpsListEntryJson =
        MpsListEntryJson(
            type = "module",
            name = module.moduleName,
            moduleKind = module.moduleKind(),
            reference = persistence.asString(module.moduleReference),
            children = children,
        )

    private fun modelEntry(model: SModel, children: List<MpsListEntryJson>? = null): MpsListEntryJson =
        MpsListEntryJson(
            type = "model",
            name = model.name.value,
            reference = persistence.asString(model.reference),
            children = children,
        )

    private fun rootEntry(node: SNode): MpsListEntryJson =
        MpsListEntryJson(
            type = "root",
            name = nodeName(node),
            concept = node.concept.qualifiedName,
            id = persistence.asString(node.nodeId),
            reference = persistence.asString(node.reference),
        )

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
