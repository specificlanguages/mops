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
        "\${COMMAND-FULL-NAME} [OPTIONS] NODE_REFERENCE",
        "       \${COMMAND-FULL-NAME} [OPTIONS] MODEL_TARGET NODE_ID",
    ],
)
class ModelGetNodeCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(
        names = ["--ancestry"],
        description = ["Nest the node's full containment chain up to the root node, not just its immediate parent."],
    )
    var ancestry: Boolean = false

    @Option(
        names = ["--depth"],
        paramLabel = "N",
        description = [
            "Export N containment levels below the node in full and the nodes at level N as stubs (role, concept, " +
                "name, reference, childCount); 0 leaves the children out. Default is the whole subtree. " +
                "Not allowed with --summary.",
        ],
    )
    var depth: Int? = null

    @Option(
        names = ["--summary"],
        description = [
            "Print only the node's type, name, concept, reference, parent, and the number of children per role. " +
                "Not allowed with --depth.",
        ],
    )
    var summary: Boolean = false

    @Option(
        names = ["--compact"],
        description = ["Print single-line JSON instead of the pretty-printed default."],
    )
    var compact: Boolean = false

    @Parameters(
        index = "0..1",
        arity = "1..2",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET NODE_ID",
        hideParamSyntax = true,
        description = ["Serialized node reference, or model target followed by node ID."],
    )
    lateinit var nodeTarget: Array<String>

    override fun run() {
        require(depth == null || depth!! >= 0) { "depth must not be negative" }
        require(!(summary && depth != null)) { "--summary cannot be combined with --depth" }

        val target = when (nodeTarget.size) {
            1 -> NodeTarget.NodeReference(nodeTarget[0])
            2 -> NodeTarget.InModel(modelTarget = nodeTarget[0], nodeId = nodeTarget[1])
            else -> error("expected one node reference or model target plus node ID")
        }
        val client = environment.daemon()

        if (summary) {
            println(renderJson(client.getNodeSummary(target, ancestry).node, pretty = !compact))
            return
        }

        val node = client.getNode(target, ancestry, depth).node
        val compactJson = renderJson(node)
        if (depth == null) {
            val size = compactJson.toByteArray(Charsets.UTF_8).size
            if (size > SIZE_HINT_THRESHOLD_BYTES) {
                System.err.println(
                    "mops: get node output is ${size / 1024} KB of JSON; pass --depth N or --summary for a bounded view",
                )
            }
        }
        println(if (compact) compactJson else renderJson(node, pretty = true))
    }

    private companion object {
        const val SIZE_HINT_THRESHOLD_BYTES = 16 * 1024
    }
}
