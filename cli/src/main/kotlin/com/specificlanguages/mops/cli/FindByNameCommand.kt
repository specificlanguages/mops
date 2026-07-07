package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(
    name = "by-name",
    description = ["Find root nodes by name using MPS Go-to-Node pattern matching. See `mops explain name-pattern`."],
)
class FindByNameCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var find: FindOperations

    @Option(
        names = ["--json"],
        description = ["Print results as JSON."],
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
        description = ["Maximum nodes to return. Defaults to 100; 0 means unlimited."],
    )
    var limit: Int = 100

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "PATTERN",
        description = ["Name pattern; supports camel-hump and '*' wildcards. See `mops explain name-pattern`."],
    )
    lateinit var pattern: String

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        require(pattern.isNotBlank()) { "pattern must not be blank" }
        val client = daemonClient ?: find.root.ensureDaemon()
        val response = client.findByName(pattern = pattern, limit = limit, all = all)
        if (json) {
            println(ProtocolJson.encodeResponse(response))
        } else {
            response.nodes.forEach(::renderText)
            if (response.truncated) {
                println(listOf("truncated", response.nodes.size, "more results not shown").joinToString("\t"))
            }
        }
    }

    private fun renderText(node: MpsNodeSummaryJson) {
        println(
            (
                listOf(
                    node.type,
                    node.name ?: "<unnamed>",
                    node.concept,
                    node.reference,
                ) + parentColumns(node.parent)
            ).joinToString("\t"),
        )
    }
}
