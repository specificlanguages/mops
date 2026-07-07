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

    /**
     * Message for a concept [name] that did not resolve to a valid **MPS Concept**. Both likely causes are stated
     * without guessing which one applies, because the two are indistinguishable from an invalid concept alone: the name
     * may be wrong, or the owning language may not be compiled or loaded into the project. The probable language is
     * named as context when the name's persisted `.structure.` spelling reveals it, since a language that was never
     * built is a common cause the raw "not found" wording hides.
     */
    fun messageForUnresolvedConcept(name: String): String {
        val languageContext = probableLanguageOf(name)?.let { " (probable owning language: $it)" } ?: ""
        return "no valid MPS Concept resolved for \"$name\"$languageContext — either the name is wrong, or its " +
            "owning language is not compiled or not loaded into the project; build the language and retry"
    }

    /** Best-effort owning language name derived from a concept [name] written with the persisted `.structure.` infix. */
    private fun probableLanguageOf(name: String): String? =
        name.indexOf(".structure.").takeIf { it > 0 }?.let { name.substring(0, it) }

    /** Best-effort qualified name of [concept]'s owning language, used to group warnings once per language. */
    fun languageName(concept: SConcept): String =
        runCatching { concept.language.qualifiedName }.getOrNull() ?: concept.qualifiedName
}
