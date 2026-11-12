package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.output.LIMIT_LIFTING_HINT
import com.specificlanguages.mops.cli.output.metadataLine
import com.specificlanguages.mops.cli.output.truncationLine
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.cli.output.TextColumn
import com.specificlanguages.mops.cli.output.nodeReferenceColumn
import com.specificlanguages.mops.cli.output.terminalHyperlinks
import com.specificlanguages.mops.cli.output.textRow
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModelCheckSummary
import com.specificlanguages.mops.protocol.ModelCheckSummaryJson

/**
 * Prints a [ModelCheckResponse] and returns the process exit code: `1` when the check found an error-severity finding
 * (judged on the totals over all findings, not only the shown ones), `0` otherwise.
 */
internal fun renderCheckResponse(
    response: ModelCheckResponse,
    outputFormat: CheckOutputFormat,
    nodeReferenceFormat: NodeReferenceFormat,
    hyperlinks: Boolean = terminalHyperlinks(),
): Int {
    when (outputFormat) {
        CheckOutputFormat.HUMAN -> {
            response.findings.forEach { renderHuman(it, nodeReferenceFormat, hyperlinks) }
            val roots = count(response.summary.rootsChecked, "root")
            println(metadataLine("checked $roots: ${countsText(response.summary)}"))
            if (response.summary.truncated) {
                println(truncationLine(response.findings.size, response.summary.total, unit = "findings"))
            }
        }

        CheckOutputFormat.JSONL -> {
            response.findings.forEach { println(renderJson(it)) }
            println(renderJson(summaryRecord(response)))
        }
    }
    return if (response.summary.errors > 0) 1 else 0
}

private fun summaryRecord(response: ModelCheckResponse): ModelCheckSummaryJson =
    ModelCheckSummaryJson(
        shown = response.findings.size,
        summary = response.summary,
        hint = if (response.summary.truncated) LIMIT_LIFTING_HINT else null,
    )

private fun renderHuman(finding: ModelCheckFindingJson, nodeReferenceFormat: NodeReferenceFormat, hyperlinks: Boolean) {
    val columns = mutableListOf(TextColumn(finding.severity.name.lowercase()), TextColumn(finding.message))
    finding.node?.let {
        columns += listOf(
            TextColumn(it.name ?: "<unnamed>"),
            TextColumn(it.concept),
            nodeReferenceColumn(it.reference, nodeReferenceFormat, hyperlinks),
        )
    }
    println(textRow(columns))
}

private fun countsText(summary: ModelCheckSummary): String {
    if (summary.total == 0) return "no findings"
    return listOf(
        count(summary.errors, "error"),
        count(summary.warnings, "warning"),
        count(summary.infos, "info", "infos"),
    ).joinToString(", ")
}

private fun count(n: Int, singular: String, plural: String = "${singular}s"): String =
    "$n ${if (n == 1) singular else plural}"
