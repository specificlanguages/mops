package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.output.LIMIT_LIFTING_HINT
import com.specificlanguages.mops.cli.output.metadataLine
import com.specificlanguages.mops.cli.output.truncationLine
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModelCheckSummaryJson

/**
 * Prints a [ModelCheckResponse] and returns the process exit code: `1` when the check found an error-severity finding
 * (judged on the totals over all findings, not only the shown ones), `0` otherwise.
 */
internal fun renderCheckResponse(
    response: ModelCheckResponse,
    outputFormat: CheckOutputFormat,
    nodeReferenceFormat: NodeReferenceFormat,
): Int {
    when (outputFormat) {
        CheckOutputFormat.HUMAN -> {
            response.findings.forEach { renderHuman(it, nodeReferenceFormat) }
            println(metadataLine(countsText(response.totals)))
            if (response.truncated) {
                println(truncationLine(response.findings.size, response.totals.total, unit = "findings"))
            }
        }

        CheckOutputFormat.JSONL -> {
            response.findings.forEach { println(renderJson(it)) }
            println(renderJson(summaryRecord(response)))
        }
    }
    return if (response.totals.errors > 0) 1 else 0
}

private fun summaryRecord(response: ModelCheckResponse): ModelCheckSummaryJson {
    val totals = response.totals
    return ModelCheckSummaryJson(
        shown = response.findings.size,
        total = totals.total,
        errors = totals.errors,
        warnings = totals.warnings,
        infos = totals.infos,
        limit = response.limit,
        truncated = response.truncated,
        hint = if (response.truncated) LIMIT_LIFTING_HINT else null,
    )
}

private fun renderHuman(finding: ModelCheckFindingJson, nodeReferenceFormat: NodeReferenceFormat) {
    val columns = mutableListOf(finding.severity.name.lowercase(), finding.message)
    finding.node?.let {
        columns += listOf(it.name ?: "<unnamed>", it.concept, renderNodeReference(it.reference, nodeReferenceFormat))
    }
    println(columns.joinToString("\t"))
}

private fun countsText(totals: ModelCheckFindingCounts): String {
    if (totals.total == 0) return "no findings"
    return listOf(
        count(totals.errors, "error"),
        count(totals.warnings, "warning"),
        count(totals.infos, "info", "infos"),
    ).joinToString(", ")
}

private fun count(n: Int, singular: String, plural: String = "${singular}s"): String =
    "$n ${if (n == 1) singular else plural}"
