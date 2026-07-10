package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindNodeByIdResponse
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModuleDiagnosticResponse
import com.specificlanguages.mops.protocol.ModulesDiagnosticsResponse
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.ModelRenderNodeResponse
import com.specificlanguages.mops.protocol.NodeFilter
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.MpsListResponse
import com.specificlanguages.mops.protocol.PongResponse

/**
 * The client talking to a remote daemon process.
 */
interface DaemonClient {
    fun ping(): PongResponse
    fun getNode(target: NodeTarget, ancestry: Boolean = false): ModelGetNodeResponse
    fun renderNode(target: NodeTarget, allowReflective: Boolean = false): ModelRenderNodeResponse

    /**
     * Runs MPS's full **Model Check** over the model named by [target] and returns the findings, bounded by [limit]
     * (`0` or less returns every finding).
     */
    fun checkModel(target: String, limit: Int): ModelCheckResponse
    fun findUsages(target: NodeTarget, scope: List<String>? = null, limit: Int): FindUsagesResponse
    fun findInstances(
        concept: String,
        exact: Boolean,
        scope: List<String>? = null,
        filters: List<NodeFilter> = emptyList(),
        limit: Int,
    ): FindInstancesResponse
    fun findByName(pattern: String, scope: List<String>? = null, limit: Int): FindByNameResponse
    fun findNodeById(nodeId: String, scope: List<String>? = null, limit: Int): FindNodeByIdResponse
    fun modelEdit(batch: EditBatch, constraints: ConstraintEnforcement = ConstraintEnforcement.BEST_EFFORT): ModelEditResponse
    fun list(
        target: List<String>?,
        depth: Int,
        limit: Int = 0,
        summary: Boolean = false,
        role: String? = null,
    ): MpsListResponse
    fun diagnoseModules(): ModulesDiagnosticsResponse
    fun diagnoseModule(module: String): ModuleDiagnosticResponse

    /**
     * Runs the MPS make on the named [modules] and their dependency closure. May block for a long time — generation and
     * compilation are unbounded — so implementations must not impose a short read timeout.
     */
    fun makeModules(modules: List<String>): MakeResponse

    /**
     * Runs the MPS make on every generatable module in the project. May block for a long time.
     */
    fun makeProject(): MakeResponse
}
