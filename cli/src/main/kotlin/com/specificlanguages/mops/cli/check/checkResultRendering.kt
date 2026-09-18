package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse

internal fun renderCheckResponse(response: ModelCheckResponse, format: String?) {
    when (resolveCheckOutputFormat(format)) {
        CheckOutputFormat.HUMAN -> {
            response.findings.forEach(::renderHuman)
            println(summaryLine(response))
        }

        CheckOutputFormat.JSONL -> response.findings.forEach { println(renderJson(it)) }
    }
}

private fun renderHuman(finding: ModelCheckFindingJson) {
    val columns = mutableListOf(finding.severity.name.lowercase(), finding.message)
    finding.node?.let { columns += listOf(it.name ?: "<unnamed>", it.concept, it.reference) }
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

private fun resolveCheckOutputFormat(requested: String?): CheckOutputFormat =
    when (requested ?: "human") {
        "human" -> CheckOutputFormat.HUMAN
        "jsonl" -> CheckOutputFormat.JSONL
        else -> throw IllegalArgumentException("unknown --format value '$requested'; expected human or jsonl")
    }

private enum class CheckOutputFormat {
    HUMAN,
    JSONL,
}
