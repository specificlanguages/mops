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
        "\${COMMAND-FULL-NAME} [OPTIONS] NODE_REFERENCE",
        "       \${COMMAND-FULL-NAME} [OPTIONS] MODEL_TARGET NODE_ID",
    ],
    footer = [
        "",
        "Prints the node the way it appears in the MPS editor, with the editor's line breaks and indentation and " +
            "trailing whitespace trimmed from each line. Any node is renderable, not only root nodes. The output is " +
            "a quick overview for reading, not a serialization that can be imported back. The target is addressed " +
            "the same way as in `mops get node`; an unresolved target fails with NODE_NOT_FOUND.",
        "",
        "If a concept in the node's subtree does not resolve because its language is not loaded, the command " +
            "fails with LANGUAGE_NOT_LOADED. It diagnoses each unloaded language the same way `find instances` " +
            "does, naming the root cause (not built, absent, or a broken dependency), and points at " +
            "`mops make module <language>` and `mops diagnose module <language>`. A concept whose language is " +
            "loaded but defines no editor is not an error: MPS renders it with its generic reflective editor " +
            "(concept aliases and roles rather than the language's own notation), and the command prints that.",
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
        index = "0..1",
        arity = "1..2",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET NODE_ID",
        hideParamSyntax = true,
        description = ["Serialized node reference, or model target followed by node ID."],
    )
    lateinit var nodeTarget: Array<String>

    override fun run() {
        val client = environment.daemon()
        val response = when (nodeTarget.size) {
            1 -> client.renderNode(NodeTarget.NodeReference(nodeTarget[0]), allowReflective)
            2 -> client.renderNode(NodeTarget.InModel(modelTarget = nodeTarget[0], nodeId = nodeTarget[1]), allowReflective)
            else -> error("expected one node reference or model target plus node ID")
        }

        println(response.text)
    }
}
