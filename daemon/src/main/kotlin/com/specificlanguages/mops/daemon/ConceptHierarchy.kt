package com.specificlanguages.mops.daemon

import jetbrains.mps.smodel.ConceptDescendantsCache
import org.jetbrains.mps.openapi.language.SAbstractConcept

/** Concept hierarchy queries over loaded language runtimes. Must run inside an MPS read action. */
object ConceptHierarchy {
    fun subConceptsOf(
        concept: SAbstractConcept,
        concreteOnly: Boolean = false,
        direct: Boolean = false,
    ): List<SAbstractConcept> {
        val cache = ConceptDescendantsCache.getInstance()
        val descendants = if (direct) cache.getDirectDescendants(concept) else cache.getDescendants(concept)
        return descendants
            .filter { it != concept && (!concreteOnly || !it.isAbstract) }
            .distinctBy { it.qualifiedName }
            .sortedBy { it.qualifiedName }
    }
}
