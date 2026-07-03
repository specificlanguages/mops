package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.MpsListResponse
import com.specificlanguages.mops.protocol.PongResponse
import java.nio.file.Path

/**
 * The client talking to a remote daemon process.
 */
interface DaemonClient {
    fun ping(): PongResponse
    fun resave(modelTarget: Path): ModelResaveResponse
    fun getNode(target: NodeTarget): ModelGetNodeResponse
    fun findUsages(target: NodeTarget, limit: Int): FindUsagesResponse
    fun findInstances(concept: String, exact: Boolean, limit: Int): FindInstancesResponse
    fun modelEdit(batch: EditBatch, constraints: ConstraintEnforcement = ConstraintEnforcement.BEST_EFFORT): ModelEditResponse
    fun list(target: List<String>?, depth: Int): MpsListResponse
}
