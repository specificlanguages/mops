@file:OptIn(ExperimentalSerializationApi::class)

package com.specificlanguages.mops.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class MpsListEntryJson(
    val type: String,
    val name: String?,
    val moduleKind: String? = null,
    val role: String? = null,
    val concept: String? = null,
    // False when a node/root entry's concept could not be resolved (its language is not loaded); omitted otherwise.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conceptValid: Boolean = true,
    val id: String? = null,
    val reference: String? = null,
    val error: String? = null,
    val children: List<MpsListEntryJson>? = null,
    // Total number of children at this level when [children] was truncated by the per-level limit; omitted when the
    // level was not truncated. The shown count is [children].size, so a consumer has both halves of "showing N of M".
    val childTotal: Int? = null,
    // Present instead of [children] when the entry was requested with --summary: counts of this level's children
    // grouped along their natural axis, rather than the children themselves.
    val summary: MpsListSummaryJson? = null,
)

/**
 * A `list --summary` breakdown of one entry's children: how they group and how many fall in each group.
 */
@Serializable
data class MpsListSummaryJson(
    // The grouping axis: "role" for a node's children, "concept" for a model's roots, "model" for a module's models,
    // or "module-kind" for a project's or the repository's modules.
    val by: String,
    val groups: List<MpsListSummaryGroupJson>,
)

/**
 * One group in a [MpsListSummaryJson]: a distinct value of the grouping axis and the number of children that share it.
 */
@Serializable
data class MpsListSummaryGroupJson(
    // The group's value of the axis: a Containment Role, a concept qualified name, a model name, or a module kind.
    val key: String,
    val count: Int,
    // For a "role" grouping, the distinct concepts of the children in this role, most frequent first — the dominant
    // concept(s) filling the role. Omitted for the other axes, whose key already names the concept or model.
    val concepts: List<String>? = null,
)
