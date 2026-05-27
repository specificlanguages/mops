package com.specificlanguages.mops.daemoncomms

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
    fun list(target: String?, depth: Int): MpsListResponse
}
