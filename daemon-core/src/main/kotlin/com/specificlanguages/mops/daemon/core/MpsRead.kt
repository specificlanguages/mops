package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.ModuleDiagnosticResponse
import com.specificlanguages.mops.protocol.ModulesDiagnosticsResponse
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget

/**
 * Read operations on the MPS repository. Operations throw [MpsRequestException] on failures that
 * carry a specific error code.
 */
interface MpsRead {
    fun list(target: List<String>?, depth: Int): MpsListEntryJson

    fun getNode(target: NodeTarget, ancestry: Boolean = false): MpsNodeJson

    /**
     * Resolves the optional `in`-clause [segments] of a find into a validated [ResolvedScope], reusing the navigation
     * grammar of [list]. A null or empty segment list yields the default editable-project-sources scope; `["/"]` yields
     * the whole repository. An ambiguous or missing segment fails with the same error shapes navigation uses.
     */
    fun resolveScope(segments: List<String>?): ResolvedScope

    fun findInstances(
        concept: String,
        exact: Boolean,
        limit: Int,
        scope: ResolvedScope = ResolvedScope.EditableProjectSources,
    ): FindInstancesResponse

    fun findByName(pattern: String, limit: Int, all: Boolean = false): FindByNameResponse

    fun findUsages(
        target: NodeTarget,
        limit: Int,
        scope: ResolvedScope = ResolvedScope.EditableProjectSources,
    ): FindUsagesResponse

    /**
     * Diagnoses the load state of the project's languages and Java-bearing modules, reporting for each unloaded one why
     * its runtime did not register.
     */
    fun diagnoseModules(): ModulesDiagnosticsResponse

    /**
     * Diagnoses the load state of one module addressed by name or serialized module reference, including whether it is
     * present in the repository at all.
     */
    fun diagnoseModule(module: String): ModuleDiagnosticResponse
}
