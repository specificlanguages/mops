package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeTarget
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(
    name = "render-node",
    description = ["Render one MPS node as the plain text of its default editor."],
)
class ModelRenderNodeCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var model: ModelOperations

    @Option(
        names = ["--allow-reflective"],
        description = [
            "Render MPS's generic reflective editor when the node's language has not been made, instead of failing.",
        ],
    )
    var allowReflective: Boolean = false

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
            1 -> client.renderNode(NodeTarget.NodeReference(nodeTarget[0]), allowReflective)
            2 -> client.renderNode(NodeTarget.InModel(modelTarget = nodeTarget[0], nodeId = nodeTarget[1]), allowReflective)
            else -> error("expected one node reference or model target plus node id")
        }

        println(response.text)
    }
}
