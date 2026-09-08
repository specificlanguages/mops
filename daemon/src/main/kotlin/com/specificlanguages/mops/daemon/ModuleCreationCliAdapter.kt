package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.DevKit
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.Memento
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

class ModuleCreationCliAdapter(private val creator: ModuleCreator) {
    private val persistence = PersistenceFacade.getInstance()

    fun createLanguage(request: CreateLanguageRequest): ModuleCreationResponse =
        if (request.dryRun) ModuleCreationResponse(plan = creator.planLanguage(request.moduleName, request.descriptor, request.withGenerator))
        else response(creator.createLanguage(request.moduleName, request.descriptor, request.withGenerator))

    fun createSolution(request: CreateSolutionRequest): ModuleCreationResponse =
        if (request.dryRun) ModuleCreationResponse(plan = creator.planSolution(request.moduleName, request.descriptor, request.usagePreset))
        else response(creator.createSolution(request.moduleName, request.descriptor, request.usagePreset))

    fun createDevkit(request: CreateDevkitRequest): ModuleCreationResponse =
        if (request.dryRun) ModuleCreationResponse(plan = creator.planDevkit(request.moduleName, request.descriptor))
        else response(creator.createDevkit(request.moduleName, request.descriptor))

    fun createGenerator(request: CreateGeneratorRequest): ModuleCreationResponse =
        if (request.dryRun) ModuleCreationResponse(plan = creator.planGenerator(request.language, request.alias, request.standalone, request.descriptor))
        else response(creator.createGenerator(request.language, request.alias, request.standalone, request.descriptor))

    private fun response(created: CreatedModules): ModuleCreationResponse = ModuleCreationResponse(
        report = ModuleCreationReport(reportEntry(created.primary), created.companions.map(::reportEntry)),
    )

    private fun reportEntry(module: SModule) = ModuleCreationEntry(
        persistence.asString(module.moduleReference), requireNotNull(module.moduleName), kind(module),
        requireNotNull((module as? AbstractModule)?.descriptorFile).path,
        (module as? Generator)?.moduleDescriptor?.sourceLanguage?.let(persistence::asString),
        (module as? Generator)?.moduleDescriptor?.alias,
        (module as? Generator)?.moduleDescriptor?.let { if (it.isStandaloneModule) GeneratorPersistence.STANDALONE else GeneratorPersistence.EMBEDDED },
        module.moduleDescriptor?.moduleFacetDescriptors?.map { facet ->
            FacetMementoJson(facet.type, facet.memento.keys.associateWith { requireNotNull(facet.memento.get(it)) }, facet.memento.text, facet.memento.children.map(::mementoJson))
        } ?: emptyList(),
    )

    private fun kind(module: SModule) = when (module) {
        is Language -> ModuleKind.LANGUAGE
        is DevKit -> ModuleKind.DEVKIT
        is Generator -> ModuleKind.GENERATOR
        else -> ModuleKind.SOLUTION
    }

    private fun mementoJson(value: Memento): MementoJson = MementoJson(
        value.type, value.keys.associateWith { requireNotNull(value.get(it)) }, value.text, value.children.map(::mementoJson),
    )
}
