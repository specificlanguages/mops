package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsNodeUsageJson
import com.specificlanguages.mops.protocol.NodeTarget
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(
    name = "usages",
    description = [
        "Find references to one MPS node. Searches editable project sources by default; append " +
            "`in <scope-segments>` to search a module, model, or subtree exhaustively, or `in /` for the whole " +
            "repository. See `mops explain scope`.",
    ],
)
class FindUsagesCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var find: FindOperations

    @Option(
        names = ["--json"],
        description = ["Print usage results as JSON."],
    )
    var json: Boolean = false

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = ["Maximum usages to return. Defaults to 100; 0 means unlimited."],
    )
    var limit: Int = 100

    @Parameters(
        arity = "1..*",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET NODE_ID [in SCOPE_SEGMENT...]",
        description = ["Serialized node reference, or model target followed by node ID, then an optional `in` clause."],
    )
    lateinit var arguments: Array<String>

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        val (targetTokens, scope) = splitUsagesArguments(arguments.toList())
        val client = daemonClient ?: find.root.ensureDaemon()
        val response = client.findUsages(target = nodeTarget(targetTokens), limit = limit, scope = scope)
        if (json) {
            println(ProtocolJson.encodeResponse(response))
        } else {
            response.usages.forEach(::renderText)
            if (response.truncated) {
                println(listOf("truncated", response.usages.size, "more results not shown").joinToString("\t"))
            }
        }
    }

    private fun nodeTarget(targetTokens: List<String>): NodeTarget =
        when (targetTokens.size) {
            1 -> NodeTarget.NodeReference(targetTokens[0])
            2 -> NodeTarget.InModel(modelTarget = targetTokens[0], nodeId = targetTokens[1])
            else -> error("expected one node reference or model target plus node id, optionally followed by `in <scope>`")
        }

    private fun renderText(usage: MpsNodeUsageJson) {
        val owner = usage.owner
        println(
            (
                listOf(
                    "usage",
                    usage.role,
                    owner.name ?: "<unnamed>",
                    owner.concept,
                    owner.reference,
                ) + parentColumns(owner.parent)
            ).joinToString("\t"),
        )
    }
}
