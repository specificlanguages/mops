package com.specificlanguages.mops.daemon

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
    private val addedNodes = mutableListOf<SNode>()
    private val referencingNodes = mutableListOf<SNode>()

    init {
        subscribeTo(repository)
    }

    override fun startListening(model: SModel) = model.addChangeListener(this)

    override fun stopListening(model: SModel) = model.removeChangeListener(this)

    override fun nodeAdded(event: SNodeAddEvent) {
        addedNodes += event.child
    }

    override fun referenceChanged(event: SReferenceChangeEvent) {
        referencingNodes += event.node
    }

    /**
     * Adds to each model the used languages and the imports of referenced models that the recorded content needs, as
     * [addMissingImportsTo] does. The content is each added node with its descendants, and each node whose
     * reference changed, without its descendants. Nodes no longer in a writable model are skipped. Returns one note per
     * added language or import.
     */
    fun addMissingImports(): List<String> {
        val content = addedNodes.flatMap { node -> SNodeUtil.getDescendants(node) } + referencingNodes
        return content.distinct()
            .mapNotNull { node -> node.model?.takeUnless { it.isReadOnly }?.let { model -> model to node } }
            .groupBy({ it.first }, { it.second })
            .flatMap { (model, nodes) -> addMissingImportsTo(model, nodes) }
    }

    override fun close() = unsubscribeFrom(repository)
}
