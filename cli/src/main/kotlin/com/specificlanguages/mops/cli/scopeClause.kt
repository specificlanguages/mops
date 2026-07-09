package com.specificlanguages.mops.cli

/**
 * Parses the trailing `in <segment>...` Search Scope clause shared by `find` subcommands.
 *
 * [tail] is the argument list that follows a subcommand's query argument(s). An empty tail means no clause and the
 * default scope. Otherwise the tail must open with the literal `in` keyword and carry at least one navigation-target
 * segment; the segments are returned verbatim for the daemon to resolve.
 */
internal fun scopeClauseSegments(tail: List<String>): List<String>? {
    if (tail.isEmpty()) return null
    require(tail.first() == "in") {
        "unexpected argument \"${tail.first()}\" — a scope clause is written `in <segment>...`; see: mops explain scope"
    }
    val segments = tail.drop(1)
    require(segments.isNotEmpty()) {
        "`in` must be followed by one or more scope segments — see: mops explain scope"
    }
    return segments
}

/**
 * Splits a `find usages` positional list into its node-target tokens and the optional scope clause.
 *
 * The node target is one token (a serialized reference) or two (a model target and a node id). The scope clause begins
 * at the first freestanding `in` argument, which is never at index 0, so a target token literally named `in` is left
 * with the query.
 */
internal fun splitUsagesArguments(positionals: List<String>): Pair<List<String>, List<String>?> {
    val inIndex = positionals.withIndex().indexOfFirst { (index, value) -> index >= 1 && value == "in" }
    val targetTokens = if (inIndex < 0) positionals else positionals.take(inIndex)
    val scope = scopeClauseSegments(if (inIndex < 0) emptyList() else positionals.drop(inIndex))
    return targetTokens to scope
}
