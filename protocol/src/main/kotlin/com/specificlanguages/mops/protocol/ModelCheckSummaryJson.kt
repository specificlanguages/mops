package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * The final record of a `check --format jsonl` stream. It carries the finding totals per severity over the full
 * **Model Check** result, how many findings were printed before it, and whether the `--limit` cap cut the list.
 * Findings have no `type` field, so a reader tells this record apart by its `type` of `summary`.
 *
 * [hint] names the option that lifts the cap and is present only when the list was truncated.
 */
@Serializable
data class ModelCheckSummaryJson(
    val type: String = "summary",
    val shown: Int,
    val total: Int,
    val errors: Int,
    val warnings: Int,
    val infos: Int,
    val limit: Int,
    val truncated: Boolean,
    val hint: String? = null,
)
