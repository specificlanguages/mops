package com.specificlanguages.mops.cli.render

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeTarget
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "node",
    description = ["Render one MPS node as the plain text of its default editor."],
    customSynopsis = [
        "mops render node [OPTIONS] NODE_REFERENCE",
        "   or: mops render node [OPTIONS] MODEL_TARGET NODE_ID",
    ],
)
class ModelRenderNodeCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(
        names = ["--allow-reflective"],
        description = [
            "Render MPS's generic reflective editor when the node's language has not been made, instead of failing.",
        ],
    )
    var allowReflective: Boolean = false

    @Parameters(
        index = "0",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET",
        description = ["Serialized node reference, or the model target of NODE_ID."],
    )
    lateinit var nodeReferenceOrModelTarget: String

    @Parameters(
        index = "1",
        arity = "0..1",
        paramLabel = "NODE_ID",
        description = ["Node ID within MODEL_TARGET, in either the decimal or the encoded spelling."],
    )
    var nodeId: String? = null

    override fun run() {
        val client = environment.daemon()
        val target = nodeId?.let { NodeTarget.InModel(modelTarget = nodeReferenceOrModelTarget, nodeId = it) }
            ?: NodeTarget.NodeReference(nodeReferenceOrModelTarget)
        val response = client.renderNode(target, allowReflective)

        println(response.text)
    }
}
