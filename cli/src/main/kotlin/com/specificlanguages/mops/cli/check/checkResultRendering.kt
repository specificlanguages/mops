package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse

internal fun renderCheckResponse(
    response: ModelCheckResponse,
    outputFormat: CheckOutputFormat,
    nodeReferenceFormat: NodeReferenceFormat,
) {
    when (outputFormat) {
        CheckOutputFormat.HUMAN -> {
            response.findings.forEach { renderHuman(it, nodeReferenceFormat) }
            println(summaryLine(response))
        }

        CheckOutputFormat.JSONL -> response.findings.forEach { println(renderJson(it)) }
    }
}

private fun renderHuman(finding: ModelCheckFindingJson, nodeReferenceFormat: NodeReferenceFormat) {
    val columns = mutableListOf(finding.severity.name.lowercase(), finding.message)
    finding.node?.let {
        columns += listOf(it.name ?: "<unnamed>", it.concept, renderNodeReference(it.reference, nodeReferenceFormat))
    }
    println(columns.joinToString("\t"))
}

private fun summaryLine(response: ModelCheckResponse): String {
    val totals = response.totals
    if (totals.total == 0) return "no findings"
    val counts = listOf(
        count(totals.errors, "error"),
        count(totals.warnings, "warning"),
        count(totals.infos, "info", "infos"),
    ).joinToString(", ")
    return if (response.truncated) "$counts (showing ${response.findings.size})" else counts
}

private fun count(n: Int, singular: String, plural: String = "${singular}s"): String =
    "$n ${if (n == 1) singular else plural}"
