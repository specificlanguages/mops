package com.specificlanguages.mops.cli

/**
 * Reports that a `--refs-only` result was truncated. The notice goes to stderr so stdout stays a clean stream of node
 * references that a consumer can pipe straight into another command.
 */
internal fun reportTruncationOnStderr(shown: Int) {
    System.err.println(listOf("truncated", shown, "more results not shown").joinToString("\t"))
}
