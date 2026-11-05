package com.specificlanguages.mops.daemon

import jetbrains.mps.smodel.ModelDependencyResolver
import jetbrains.mps.smodel.ModelDependencyScanner
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.language.LanguageRegistry
import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeUtil
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryContentAdapter

/**
 * Records the nodes added to the models of [repository] and the nodes whose references change, from construction until
 * [close], so [addMissingImports] can give each model the used languages and model imports that this content needs.
 * Content the models held before is not looked at, so references that already relied on an undeclared import stay as
 * they were. Construct, use, and close it with write access.
 */
internal class MissingImportsTracker(private val repository: SRepository) : SRepositoryContentAdapter(), AutoCloseable {
    private val changedNodes = mutableListOf<SNode>()

    init {
        subscribeTo(repository)
    }

    override fun startListening(model: SModel) = model.addChangeListener(this)

    override fun stopListening(model: SModel) = model.removeChangeListener(this)

    override fun nodeAdded(event: SNodeAddEvent) {
        changedNodes += event.child
    }

    override fun referenceChanged(event: SReferenceChangeEvent) {
        changedNodes += event.node
    }

    /**
     * Adds to each model the used languages of the recorded nodes and their descendants, and imports of the models
     * their references point to, unless the model already declares them. Languages that a used devkit brings count as
     * declared, and so do accessory models of used languages, as in MPS's `ModelDependencyUpdate`. Nodes no longer in a
     * writable model are skipped. Returns one note per added language or import.
     */
    fun addMissingImports(): List<String> {
        val resolver = ModelDependencyResolver(LanguageRegistry.getInstance(repository), repository)
        return changedNodes
            .filter { node -> node.model?.isReadOnly == false }
            .groupBy { node -> node.model!! }
            .flatMap { (model, nodes) -> addMissingImports(model, nodes, resolver) }
    }

    private fun addMissingImports(model: SModel, nodes: List<SNode>, resolver: ModelDependencyResolver): List<String> {
        val scanned = nodes.flatMap { node -> SNodeUtil.getDescendants(node) }.distinct()
        val scanner = ModelDependencyScanner().walk(scanned)
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

    override fun close() = unsubscribeFrom(repository)
}
