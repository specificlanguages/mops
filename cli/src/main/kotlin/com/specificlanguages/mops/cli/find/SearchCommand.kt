package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.cli.output.truncationLine
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.SearchScopeJson
import picocli.CommandLine.Option

/**
 * Base class for `find` subcommands that return a bounded list of matches. It declares the output options and
 * `--limit`, checks them, and prints the [SearchResults] that [search] returns in the chosen output mode. Subclasses
 * declare the query arguments and ask the daemon.
 */
abstract class SearchCommand(protected val environment: CommandEnvironment) : CliCommand() {
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
                "truncation and an empty result are reported on stderr.",
        ],
    )
    var refsOnly: Boolean = false

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = [
            "Maximum results to return. Defaults to 100; 0 means unlimited; negative values are rejected. Only when " +
                "more matches exist, text output ends with a `# truncated` line and JSON sets `truncated`.",
        ],
    )
    var limit: Int = 100

    final override fun run() {
        checkQuery()
        require(limit >= 0) { "limit must not be negative" }
        require(!(refsOnly && json)) { "--refs-only cannot be combined with --json" }
        printResults(search())
    }

    /** Rejects a query argument before the options are checked. Does nothing unless a subclass overrides it. */
    protected open fun checkQuery() = Unit

    /** Reads the remaining arguments, asks the daemon, and returns what it found. Runs after the options are checked. */
    protected abstract fun search(): SearchResults

    /**
     * Prints the matches in the chosen output mode. When there are none, text and `--refs-only` output carry an
     * [emptyResultLine] naming the subject and the searched scope; JSON output is the response alone.
     */
    private fun printResults(results: SearchResults) {
        val matches = results.matches
        if (!json && matches.isEmpty()) {
            printMetadata(emptyResultLine(results.subject, results.scope, environment.nodeReferenceFormat), refsOnly)
            return
        }
        when {
            json -> println(renderJson(results.response))
            refsOnly -> {
                matches.forEach {
                    println(renderNodeReference(it.reference, environment.nodeReferenceFormat.withoutLink()))
                }
                if (results.truncated) reportTruncationOnStderr(matches.size)
            }
            else -> {
                matches.forEach { println(it.row) }
                if (results.truncated) println(truncationLine(shown = matches.size))
            }
        }
    }

    /**
     * What a search found. [response] is printed as is under `--json`. [subject] names what was searched for in the
     * empty-result line, such as `usages of foo (ClassConcept)`, and [scope] is the scope the daemon searched.
     */
    protected class SearchResults(
        val response: DaemonResponse,
        val subject: String,
        val scope: SearchScopeJson,
        val matches: List<SearchMatch>,
        val truncated: Boolean,
    )

    /** One match: the serialized node reference that `--refs-only` prints, and the row that text output prints. */
    protected class SearchMatch(val reference: String, val row: String)
}
