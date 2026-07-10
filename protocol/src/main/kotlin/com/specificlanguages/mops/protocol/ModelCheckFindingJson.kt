package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Severity of a **Model Check** finding, mirroring MPS's own message levels. Ordered most severe first so findings sort
 * naturally by `ordinal`.
 */
@Serializable
enum class FindingSeverity {
    @SerialName("error")
    ERROR,

    @SerialName("warning")
    WARNING,

    @SerialName("info")
    INFO,
}

/**
 * One finding from running MPS's full **Model Check** over a model: its [severity] and [message], the offending **MPS
 * Node** when the finding is attached to one (its [node] carries the same Node Reference / name / **MPS Concept**
 * vocabulary `get-node` and `find` emit), and the [category]/[rule] the checker attributed it to when available.
 *
 * [node] is null for a model-level finding that names no node.
 */
@Serializable
data class ModelCheckFindingJson(
    val severity: FindingSeverity,
    val message: String,
    val node: MpsNodeSummaryJson? = null,
    val category: String? = null,
    val rule: String? = null,
)
