package com.specificlanguages.mops.cli.render

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.common.NodeTargetParameters
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

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

    @Mixin
    val nodeTarget = NodeTargetParameters()

    override fun run() {
        val client = environment.daemon()
        val response = client.renderNode(nodeTarget.target(), allowReflective)

        println(response.text)
    }
}
