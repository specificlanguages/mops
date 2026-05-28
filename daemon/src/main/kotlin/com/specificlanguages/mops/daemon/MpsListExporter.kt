package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsListEntryJson
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.Project
import jetbrains.mps.project.Solution
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
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
                    .map(::moduleEntry)
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
                    .map(::moduleEntry)
                    .toList()
            } else {
                null
            },
        )

    private fun moduleEntry(module: SModule): MpsListEntryJson =
        MpsListEntryJson(
            type = "module",
            name = module.moduleName,
            moduleKind = module.moduleKind(),
            reference = persistence.asString(module.moduleReference),
        )

    private fun SModule.moduleKind(): String =
        when (this) {
            is Language -> "language"
            is Generator -> "generator"
            is Solution -> "solution"
            is DevKit -> "devkit"
            else -> "other"
        }
}
