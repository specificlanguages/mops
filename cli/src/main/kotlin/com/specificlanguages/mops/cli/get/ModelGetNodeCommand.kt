package com.specificlanguages.mops.cli.get

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.common.NodeTargetParameters
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

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

    @Mixin
    val nodeTarget = NodeTargetParameters()

    override fun run() {
        val client = environment.daemon()
        val response = client.getNode(nodeTarget.target(), ancestry)

        println(renderJson(response.node))
    }
}
