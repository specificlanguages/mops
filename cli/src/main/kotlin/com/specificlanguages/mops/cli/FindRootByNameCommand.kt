package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(
    name = "root-by-name",
    description = [
        "Find Root Nodes by name using MPS Go-to-Node pattern matching. Searches editable project sources by " +
            "default; append `in <scope-segments>` to search a module, model, or the whole repository (`in /`). Only " +
            "root-bearing scopes are valid. See `mops explain name-pattern` and `mops explain scope`.",
    ],
)
class FindRootByNameCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var find: FindOperations

    @Option(
        names = ["--json"],
        description = ["Print results as JSON."],
    )
    var json: Boolean = false

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

    @Parameters(
        index = "1..*",
        paramLabel = "[in SCOPE_SEGMENT...]",
        description = ["Optional Search Scope clause: the literal `in` followed by navigation-target segments."],
    )
    var scopeClause: List<String> = emptyList()

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        require(pattern.isNotBlank()) { "pattern must not be blank" }
        val scope = scopeClauseSegments(scopeClause)
        val client = daemonClient ?: find.root.ensureDaemon()
        val response = client.findByName(pattern = pattern, limit = limit, scope = scope)
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
