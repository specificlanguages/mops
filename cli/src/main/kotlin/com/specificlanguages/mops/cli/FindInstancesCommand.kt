package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(
    name = "instances",
    description = [
        "Find instances of one MPS concept. Searches editable project sources by default; append " +
            "`in <scope-segments>` to search a module, model, or subtree exhaustively, or `in /` for the whole " +
            "repository. See `mops explain scope`.",
    ],
)
class FindInstancesCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var find: FindOperations

    @Option(
        names = ["--json"],
        description = ["Print instance results as JSON."],
    )
    var json: Boolean = false

    @Option(
        names = ["--exact"],
        description = ["Match only nodes whose direct concept is the queried concept."],
    )
    var exact: Boolean = false

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = ["Maximum instances to return. Defaults to 100; 0 means unlimited."],
    )
    var limit: Int = 100

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "CONCEPT",
        description = ["Fully qualified MPS concept name."],
    )
    lateinit var concept: String

    @Parameters(
        index = "1..*",
        paramLabel = "[in SCOPE_SEGMENT...]",
        description = ["Optional Search Scope clause: the literal `in` followed by navigation-target segments."],
    )
    var scopeClause: List<String> = emptyList()

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        val scope = scopeClauseSegments(scopeClause)
        val client = daemonClient ?: find.root.ensureDaemon()
        val response = client.findInstances(concept = concept, exact = exact, limit = limit, scope = scope)
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
