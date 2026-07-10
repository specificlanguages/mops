package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ModuleDiagnosticResponse
import com.specificlanguages.mops.protocol.ModulesDiagnosticsResponse
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.MpsListResponse
import com.specificlanguages.mops.protocol.PongResponse

/**
 * The client talking to a remote daemon process.
 */
interface DaemonClient {
    fun ping(): PongResponse
    fun getNode(target: NodeTarget, ancestry: Boolean = false): ModelGetNodeResponse
    fun findUsages(target: NodeTarget, limit: Int, scope: List<String>? = null): FindUsagesResponse
    fun findInstances(concept: String, exact: Boolean, limit: Int, scope: List<String>? = null): FindInstancesResponse
    fun findByName(pattern: String, limit: Int, scope: List<String>? = null): FindByNameResponse
    fun modelEdit(batch: EditBatch, constraints: ConstraintEnforcement = ConstraintEnforcement.BEST_EFFORT): ModelEditResponse
    fun list(target: List<String>?, depth: Int): MpsListResponse
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
