package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindNodeByIdResponse
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.NodeFilter
import com.specificlanguages.mops.protocol.ModuleDiagnosticResponse
import com.specificlanguages.mops.protocol.ModulesDiagnosticsResponse
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget

/**
 * Read operations on the MPS repository. Operations throw [MpsRequestException] on failures that
 * carry a specific error code.
 */
interface MpsRead {
    /**
     * Lists one navigation target as a bounded semantic tree. [depth] bounds descent; [limit] caps each level's
     * children (0 = unbounded), recording the total on a truncated level. When [summary] is set the target's children
     * are returned as grouped counts instead of enumerated. [role], valid only for a node target, restricts the listed
     * children to one Containment Role.
     */
    fun list(
        target: List<String>?,
        depth: Int,
        limit: Int = 0,
        summary: Boolean = false,
        role: String? = null,
    ): MpsListEntryJson

    fun getNode(target: NodeTarget, ancestry: Boolean = false): MpsNodeJson

    /**
     * Runs MPS's full **Model Check** (typesystem and checking rules) over the model addressed by [target] (the same
     * model-target grammar `getNode` uses) and returns the findings, sorted most severe first and bounded by [limit]
     * (a [limit] of `0` or less returns every finding). Read-only: the model is never modified or saved. Fails with
     * [MpsErrorCode.MODEL_NOT_FOUND] when the target resolves to no model, or [MpsErrorCode.AMBIGUOUS_TARGET] when it
     * matches more than one.
     */
    fun checkModel(target: String, limit: Int): ModelCheckResponse

    /**
     * Resolves the optional `in`-clause [segments] of a find into a validated [ResolvedScope], reusing the navigation
     * grammar of [list]. A null or empty segment list yields the default editable-project-sources scope; `["/"]` yields
     * the whole repository. An ambiguous or missing segment fails with the same error shapes navigation uses.
     */
    fun resolveScope(segments: List<String>?): ResolvedScope

    /**
     * Finds instances of [concept] within [scope], keeping only those that satisfy every [filter][filters]. The filters
     * are AND-composed post-filters over the concept matches (see [NodeFilter]); a subtree [scope] adds its own
     * containment predicate to the same chain. An empty [filters] list applies no post-filtering.
     */
    fun findInstances(
        concept: String,
        exact: Boolean,
        scope: ResolvedScope = ResolvedScope.EditableProjectSources,
        filters: List<NodeFilter> = emptyList(),
        limit: Int,
    ): FindInstancesResponse

    /**
     * Finds root nodes whose name matches [pattern]. Only root-bearing scopes are valid — the default editable project
     * sources, the repository, a module, or a model. A [ResolvedScope.Subtree] scope holds no Root Nodes and is
     * rejected with an error pointing at `find instances --named` for named descendants.
     */
    fun findByName(
        pattern: String,
        scope: ResolvedScope = ResolvedScope.EditableProjectSources,
        limit: Int,
    ): FindByNameResponse

    fun findUsages(
        target: NodeTarget,
        scope: ResolvedScope = ResolvedScope.EditableProjectSources,
        limit: Int,
    ): FindUsagesResponse

    /**
     * Finds every node whose **Node ID** equals [nodeId] across all models in [scope], one match per model that holds
     * it — a Node ID is unique only within its model. [nodeId] accepts either spelling (decimal or the persisted encoded
     * form). A malformed id fails with [MpsErrorCode.INVALID_REQUEST]; a well-formed id matching nothing returns an
     * empty result, which is a success.
     */
    fun findNodeById(
        nodeId: String,
        scope: ResolvedScope = ResolvedScope.EditableProjectSources,
        limit: Int,
    ): FindNodeByIdResponse

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
