package com.specificlanguages.mops.daemon

import groovy.lang.Closure
import jetbrains.mps.progress.EmptyProgressMonitor
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SReference
import org.jetbrains.mps.openapi.module.FindUsagesFacade
import org.jetbrains.mps.openapi.module.SearchScope

object MopsSearchExtensions {
    /** Streams native references to the supplied node. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun eachUsageOf(search: MopsSearch, node: SNode, scope: SearchScope, body: Closure<*>) {
        requireRead(search.mops, "mops.search.eachUsageOf")
        FindUsagesFacade.getInstance().findUsages(
            scope,
            setOf(node),
            { reference: SReference -> body.call(reference) },
            EmptyProgressMonitor(),
        )
    }

    /** Streams native instances of the supplied concept, including subconcepts by default. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    @JvmOverloads
    fun eachInstanceOf(
        search: MopsSearch,
        concept: SAbstractConcept,
        scope: SearchScope,
        exact: Boolean = false,
        body: Closure<*>,
    ) {
        requireRead(search.mops, "mops.search.eachInstanceOf")
        FindUsagesFacade.getInstance().findInstances(
            scope,
            setOf(concept),
            exact,
            { node: SNode -> body.call(node) },
            EmptyProgressMonitor(),
        )
    }
}
