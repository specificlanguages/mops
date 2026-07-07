package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(name = "get-node", description = ["Export one MPS node as JSON."])
class ModelGetNodeCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var model: ModelOperations

    @Option(
        names = ["--ancestry"],
        description = ["Nest the node's full containment chain up to the root node, not just its immediate parent."],
    )
    var ancestry: Boolean = false

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
            1 -> client.getNode(NodeTarget.NodeReference(nodeTarget[0]), ancestry)
            2 -> client.getNode(NodeTarget.InModel(modelTarget = nodeTarget[0], nodeId = nodeTarget[1]), ancestry)
            else -> error("expected one node reference or model target plus node id")
        }

        println(ProtocolJson.encodeNode(response.node))
    }
}
