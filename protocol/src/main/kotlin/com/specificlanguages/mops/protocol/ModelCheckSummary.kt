package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * What a **Model Check** found as a whole, as opposed to the findings themselves. Every `check` output format reports
 * these facts: the daemon sends them in [ModelCheckResponse], and the JSONL summary record ([ModelCheckSummaryJson])
 * prints each of them as a field.
 *
 * The counts per [severity][FindingSeverity] are taken over the full finding set before truncation, so "zero errors" is
 * distinguishable from "errors present" even when only the most severe slice was sent. [truncated] is true when
 * findings beyond [limit] were dropped.
 */
@Serializable
data class ModelCheckSummary(
    val errors: Int,
    val warnings: Int,
    val infos: Int,
    val limit: Int,
    val truncated: Boolean,
) {
    val total: Int get() = errors + warnings + infos

    companion object {
        /** Summarizes [findings], the full result before truncation, for a check whose report is capped at [limit]. */
        fun of(findings: List<ModelCheckFindingJson>, limit: Int): ModelCheckSummary =
            ModelCheckSummary(
                errors = findings.count { it.severity == FindingSeverity.ERROR },
                warnings = findings.count { it.severity == FindingSeverity.WARNING },
                infos = findings.count { it.severity == FindingSeverity.INFO },
                limit = limit,
                truncated = limit > 0 && findings.size > limit,
            )
    }
}
