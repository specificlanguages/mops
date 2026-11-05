package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.cli.output.truncationLine
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

/**
 * Base class for `find` subcommands that return a bounded list of nodes. It declares the shared output options, the
 * `--limit` option and the trailing scope clause, and prints results in the chosen output mode. Subclasses declare
 * their query argument at index 0.
 */
abstract class NodeSearchCommand(protected val environment: CommandEnvironment) : CliCommand() {
    @Option(
        names = ["--json"],
        description = ["Print results as JSON."],
    )
    var json: Boolean = false

    @Option(
        names = ["--full-concept"],
        description = ["Show fully qualified concept names in text output instead of short names."],
    )
    var fullConcept: Boolean = false

    @Option(
        names = ["--refs-only"],
        description = [
            "Print one serialized node reference per line and nothing else, for piping. Cannot combine with --json; " +
                "truncation is reported on stderr.",
        ],
    )
    var refsOnly: Boolean = false

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = [
            "Maximum nodes to return. Defaults to 100; 0 means unlimited; negative values are rejected. Only when " +
                "more matches exist, text output ends with a `# truncated` line and JSON sets `truncated`.",
        ],
    )
    var limit: Int = 100

    @Parameters(
        index = "1..*",
        paramLabel = "[in SCOPE_SEGMENT...]",
        description = ["Optional search scope clause: the literal `in` followed by navigation-target segments."],
    )
    var scopeClause: List<String> = emptyList()

    /** Checks the shared options and returns the parsed scope segments, or `null` for the default scope. */
    protected fun validatedScope(): List<String>? {
        require(limit >= 0) { "limit must not be negative" }
        require(!(refsOnly && json)) { "--refs-only cannot be combined with --json" }
        return scopeClauseSegments(scopeClause)
    }

    protected fun printResults(response: DaemonResponse, nodes: List<MpsNodeSummaryJson>, truncated: Boolean) {
        when {
            json -> println(renderJson(response))
            refsOnly -> {
                nodes.forEach {
                    println(renderNodeReference(it.reference, environment.nodeReferenceFormat, hyperlinks = false))
                }
                if (truncated) reportTruncationOnStderr(nodes.size)
            }
            else -> {
                nodes.forEach { println(renderText(it, fullConcept, environment.nodeReferenceFormat)) }
                if (truncated) println(truncationLine(shown = nodes.size))
            }
        }
    }
}
