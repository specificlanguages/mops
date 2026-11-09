package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Helpers for an **MPS Node** whose **MPS Concept** did not resolve to a valid concept
 * ([org.jetbrains.mps.openapi.language.SAbstractConcept.isValid] == false), which almost always means the owning
 * language was never compiled.
 *
 * Read commands annotate such a node (`conceptValid = false`) rather than failing. The write path uses these helpers to
 * apply [com.specificlanguages.mops.protocol.ConstraintEnforcement] to such a node and to name its unloaded language.
 * Code Mode role and property lookups fail on such a node, because its concept has no roles or properties to look up.
 */
object ConceptValidityGuard {

    fun messageFor(node: SNode): String {
        val address = runCatching { PersistenceFacade.getInstance().asString(node.reference) }.getOrNull()
            ?: node.nodeId.toString()
        return "MPS concept of node $address could not be resolved (${node.concept.qualifiedName}); " +
            notCompiledHint(node.concept)
    }

    /** Fails with the unloaded language when [concept] did not resolve; [lookup] names what could not be looked up. */
    fun requireValid(concept: SConcept, lookup: String) {
        check(concept.isValid) {
            "cannot look up $lookup: concept ${concept.qualifiedName} is not loaded; ${notCompiledHint(concept)}"
        }
    }

    private fun notCompiledHint(concept: SConcept): String {
        val language = languageName(concept)
        return "its owning language '$language' is most likely not compiled — run 'mops make module $language' and retry"
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
