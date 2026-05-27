package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.GsonCodec
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(name = "get-node", description = ["Export one loaded MPS node as JSON."])
class ModelGetNodeCommand(private val daemonClient: DaemonClient? = null) : Runnable {
    @ParentCommand
    lateinit var model: ModelOperations

    @Spec
    lateinit var spec: CommandSpec

    @Parameters(
        index = "0..1",
        arity = "1..2",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET NODE_ID",
        description = ["Serialized node reference, or model target followed by node ID."],
    )
    lateinit var nodeTarget: Array<String>

    override fun run() {
        val client = daemonClient ?: model.root.ensureDaemon()
        val response = when (nodeTarget.size) {
            1 -> client.getNode(NodeTarget.NodeReference(nodeTarget[0]))
            2 -> client.getNode(NodeTarget.InModel(modelTarget = nodeTarget[0], nodeId = nodeTarget[1]))
            else -> error("expected one node reference or model target plus node id")
        }

        spec.commandLine().out.println(GsonCodec.toJson(response.node))
    }
}
