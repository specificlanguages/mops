package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Helpers for an **MPS Node** whose **MPS Concept** did not resolve to a valid concept
 * ([org.jetbrains.mps.openapi.language.SAbstractConcept.isValid] == false), which almost always means the owning
 * language was never compiled.
 *
 * Read paths annotate such a node (`conceptValid = false`) rather than failing. The write path uses these helpers to
 * abort under strict enforcement and to name the unloaded language when it reports a skipped check as a warning.
 */
object ConceptValidityGuard {

    fun messageFor(node: SNode): String {
        val address = runCatching { PersistenceFacade.getInstance().asString(node.reference) }.getOrNull()
            ?: node.nodeId.toString()
        return "MPS Concept of node $address could not be resolved (${node.concept.qualifiedName}); " +
            "its owning language is most likely not compiled — compile the language and retry"
    }

    /** Best-effort qualified name of [concept]'s owning language, used to group warnings once per language. */
    fun languageName(concept: SConcept): String =
        runCatching { concept.language.qualifiedName }.getOrNull() ?: concept.qualifiedName

    /**
     * The qualified names, sorted and de-duplicated, of the languages of every unresolved **MPS Concept** anywhere in
     * [node]'s containment subtree (the node and all its descendants). Empty when every concept resolved. Callers use
     * this to refuse an operation that needs valid concepts throughout the subtree, such as editor rendering, and to
     * name the languages to make.
     */
    fun unresolvedLanguages(node: SNode): List<String> {
        val languages = sortedSetOf<String>()
        collectUnresolvedLanguages(node, languages)
        return languages.toList()
    }

    private fun collectUnresolvedLanguages(node: SNode, into: MutableSet<String>) {
        if (!node.concept.isValid) {
            into.add(languageName(node.concept))
        }
        for (child in node.children) {
            collectUnresolvedLanguages(child, into)
        }
    }
}
