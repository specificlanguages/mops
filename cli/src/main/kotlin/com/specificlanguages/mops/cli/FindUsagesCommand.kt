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

@Command(name = "usages", description = ["Find references to one MPS node."])
class FindUsagesCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var find: FindOperations

    @Option(
        names = ["--json"],
        description = ["Print usage results as JSON."],
    )
    var json: Boolean = false

    @Option(
        names = ["--all"],
        description = ["Search all models, including read-only libraries and stubs, not just editable project sources."],
    )
    var all: Boolean = false

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = ["Maximum usages to return. Defaults to 100; 0 means unlimited."],
    )
    var limit: Int = 100

    @Parameters(
        index = "0..1",
        arity = "1..2",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET NODE_ID",
        description = ["Serialized node reference, or model target followed by node ID."],
    )
    lateinit var nodeTarget: Array<String>

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        val client = daemonClient ?: find.root.ensureDaemon()
        val response = client.findUsages(target = nodeTarget(), limit = limit, all = all)
        if (json) {
            println(ProtocolJson.encodeResponse(response))
        } else {
            response.usages.forEach(::renderText)
            if (response.truncated) {
                println(listOf("truncated", response.usages.size, "more results not shown").joinToString("\t"))
            }
        }
    }

    private fun nodeTarget(): NodeTarget =
        when (nodeTarget.size) {
            1 -> NodeTarget.NodeReference(nodeTarget[0])
            2 -> NodeTarget.InModel(modelTarget = nodeTarget[0], nodeId = nodeTarget[1])
            else -> error("expected one node reference or model target plus node id")
        }

    private fun renderText(usage: MpsNodeUsageJson) {
        val owner = usage.owner
        println(
            listOf(
                "usage",
                usage.role,
                owner.name ?: "<unnamed>",
                owner.concept,
                owner.reference,
            ).joinToString("\t"),
        )
    }
}
