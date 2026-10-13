package com.specificlanguages.mops.daemon

import jetbrains.mps.project.Project
import jetbrains.mps.smodel.language.LanguageRegistry
import org.jetbrains.mps.openapi.language.SAbstractConcept

/** Concept hierarchy queries over loaded language runtimes. Must run inside an MPS read action. */
class ConceptHierarchy(private val project: Project) {
    fun subConceptsOf(
        concept: SAbstractConcept,
        concreteOnly: Boolean = false,
        direct: Boolean = false,
    ): List<SAbstractConcept> =
        project.getComponent(LanguageRegistry::class.java).allLanguages
            .flatMap { it.concepts }
            .filter { candidate ->
                candidate != concept && (!concreteOnly || !candidate.isAbstract) &&
                    if (direct) {
                        candidate.superConcept == concept || candidate.superInterfaces.any { it == concept }
                    } else {
                        candidate.isSubConceptOf(concept)
                    }
            }
            .distinctBy { it.qualifiedName }
            .sortedBy { it.qualifiedName }
}
