package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Total **Model Check** finding counts per [severity][FindingSeverity], computed over the full finding set before any
 * truncation. Lets a caller report how many findings of each severity a model has even when only the most severe slice
 * was sent, so "zero errors" is distinguishable from "errors present" without re-running the check unbounded.
 */
@Serializable
data class ModelCheckFindingCounts(
    val errors: Int,
    val warnings: Int,
    val infos: Int,
) {
    val total: Int get() = errors + warnings + infos

    companion object {
        fun of(findings: List<ModelCheckFindingJson>): ModelCheckFindingCounts =
            ModelCheckFindingCounts(
                errors = findings.count { it.severity == FindingSeverity.ERROR },
                warnings = findings.count { it.severity == FindingSeverity.WARNING },
                infos = findings.count { it.severity == FindingSeverity.INFO },
            )
    }
}
