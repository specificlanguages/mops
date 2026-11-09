package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.SearchScopeJson
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

/**
 * Base class for `find` subcommands that take one query argument, then an optional scope clause, and return nodes.
 * Subclasses declare their query argument at index 0. It parses the scope clause and prints each node as a
 * [renderText] row. Its help footer describes that output; picocli replaces it for a subclass that declares its own
 * footer.
 */
@Command(footer = ["", NODE_RESULTS_HELP])
abstract class NodeSearchCommand(environment: CommandEnvironment) : SearchCommand(environment) {
    @Parameters(
        index = "1..*",
        paramLabel = "[in SCOPE_SEGMENT...]",
        description = ["Optional search scope clause: the literal `in` followed by navigation-target segments."],
    )
    var scopeClause: List<String> = emptyList()

    final override fun search(): SearchResults = searchIn(scopeClauseSegments(scopeClause))

    /** Asks the daemon for the query in [scope], the parsed scope segments, or `null` for the default scope. */
    protected abstract fun searchIn(scope: List<String>?): SearchResults

    /** The [nodes] of [response] as search results, with [subject] naming what was searched for. */
    protected fun nodeResults(
        response: DaemonResponse,
        subject: String,
        scope: SearchScopeJson,
        nodes: List<MpsNodeSummaryJson>,
        truncated: Boolean,
    ): SearchResults = SearchResults(
        response = response,
        subject = subject,
        scope = scope,
        matches = nodes.map { SearchMatch(it.reference, renderText(it, fullConcept, environment.nodeReferenceFormat)) },
        truncated = truncated,
    )
}
