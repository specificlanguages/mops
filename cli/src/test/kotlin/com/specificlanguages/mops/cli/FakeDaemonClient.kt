package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.PongResponse
import java.nio.file.Path

object FakeDaemonClient : DaemonClient {
    override fun ping(): PongResponse = throw UnsupportedOperationException()
    override fun resave(modelTarget: Path): ModelResaveResponse = throw UnsupportedOperationException()
    override fun getNode(target: NodeTarget): ModelGetNodeResponse =
        throw UnsupportedOperationException()
}
