package com.specificlanguages.mops.daemon

import jetbrains.mps.smodel.ModelDependencyResolver
import jetbrains.mps.smodel.ModelDependencyScanner
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.language.LanguageRegistry
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode

/**
 * Adds to [model] the used languages of [nodes] and imports of the models their references point to, unless [model]
 * already declares them. Only the given nodes are scanned, not their descendants. Languages that a used devkit brings
 * count as declared, and so do accessory models of used languages, as in MPS's `ModelDependencyUpdate`. Nothing is
 * removed. Returns one note per added language or import. Requires write access and a model in a repository.
 */
internal fun addMissingImports(model: SModel, nodes: Iterable<SNode>): List<String> {
    val repository = requireNotNull(model.repository) { "model ${model.name.value} is not in a repository" }
    val resolver = ModelDependencyResolver(LanguageRegistry.getInstance(repository), repository)
    val scanner = ModelDependencyScanner().walk(nodes)
    val imports = ModelImports(model)
    val modelName = model.name.value

    val declaredLanguages = resolver.usedLanguages(model).toHashSet()
    val addedLanguages = scanner.usedLanguages.sortedBy { it.qualifiedName }.filter(declaredLanguages::add)
    addedLanguages.forEach(imports::addUsedLanguage)

    // Read after the languages are added, so the accessory models of an added language count as imported.
    val visibleModels = imports.importedModels.toHashSet()
    resolver.implicitImports(model).mapTo(visibleModels) { it.reference }
    val addedImports = scanner.crossModelReferences.sortedBy { it.name.value }.filter(visibleModels::add)
    addedImports.forEach(imports::addModelImport)

    return addedLanguages.map { "added used language ${it.qualifiedName} to model $modelName" } +
        addedImports.map { "added model import ${it.name.value} to model $modelName" }
}
