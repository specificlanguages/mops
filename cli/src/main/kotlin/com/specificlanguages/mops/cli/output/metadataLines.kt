package com.specificlanguages.mops.cli.output

/** The phrase that tells a caller how to lift the `--limit` cap, worded the same in every format. */
internal const val LIMIT_LIFTING_HINT = "pass --limit 0 for all"

/**
 * Renders one metadata line for row-shaped text output. Metadata is what mops understood and what it left out
 * (truncation, the scope searched, zero matches). It travels with the results on stdout but is `#`-prefixed so a
 * consumer can tell it from a result row, for example with `grep -v '^#'`.
 */
internal fun metadataLine(text: String): String = "# $text"

/**
 * Metadata line reporting that a result was cut at the `--limit` cap. It gives the number of entries shown, the total
 * where it is known, and the option that lifts the cap, so a caller who did not choose the default limit learns how to
 * get everything. [unit] names what was counted, for example `children`.
 */
internal fun truncationLine(shown: Int, total: Int? = null, unit: String? = null): String {
    val count = listOfNotNull(shown.toString(), total?.let { "of $it" }, unit).joinToString(" ")
    return metadataLine("truncated: showing $count; $LIMIT_LIFTING_HINT")
}
