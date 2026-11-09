package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * What a **Model Check** found as a whole, as opposed to the findings themselves. The daemon sends it in
 * [ModelCheckResponse], and the JSONL summary record ([ModelCheckSummaryJson]) prints each of its fields. Text output
 * prints the counts and, when the list was truncated, a truncation line.
 *
 * [rootsChecked] counts the root nodes in the checked models; zero means the selection had nothing to check.
 *
 * The counts per [severity][FindingSeverity] are taken over the full finding set before truncation, so "zero errors" is
 * distinguishable from "errors present" even when only the most severe slice was sent. [truncated] is true when
 * findings beyond [limit] were dropped.
 */
@Serializable
data class ModelCheckSummary(
    val rootsChecked: Int,
    val errors: Int,
    val warnings: Int,
    val infos: Int,
    val limit: Int,
    val truncated: Boolean,
) {
    val total: Int get() = errors + warnings + infos

    companion object {
        /**
         * Summarizes [findings], the full result before truncation, for a check of [rootsChecked] root nodes whose
         * report is capped at [limit].
         */
        fun of(findings: List<ModelCheckFindingJson>, limit: Int, rootsChecked: Int): ModelCheckSummary =
            ModelCheckSummary(
                rootsChecked = rootsChecked,
                errors = findings.count { it.severity == FindingSeverity.ERROR },
                warnings = findings.count { it.severity == FindingSeverity.WARNING },
                infos = findings.count { it.severity == FindingSeverity.INFO },
                limit = limit,
                truncated = limit > 0 && findings.size > limit,
            )
    }
}
