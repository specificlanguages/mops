package com.specificlanguages.mops.cli.get

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeTarget
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "node",
    description = ["Export one MPS node as JSON."],
    customSynopsis = [
        "mops get node [OPTIONS] NODE_REFERENCE",
        "   or: mops get node [OPTIONS] MODEL_TARGET NODE_ID",
    ],
)
class ModelGetNodeCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(
        names = ["--ancestry"],
        description = ["Nest the node's full containment chain up to the root node, not just its immediate parent."],
    )
    var ancestry: Boolean = false

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
        val response = client.getNode(target, ancestry)

        println(renderJson(response.node))
    }
}
