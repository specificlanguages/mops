@file:OptIn(ExperimentalSerializationApi::class)

package com.specificlanguages.mops.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class MpsNodeUsageJson(
    val role: String,
    val owner: MpsNodeSummaryJson,
)

@Serializable
data class MpsNodeSummaryJson(
    val type: String,
    val name: String?,
    val concept: String,
    // False when the node's concept could not be resolved (its language is not loaded); omitted on the happy path.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conceptValid: Boolean = true,
    val reference: String,
    // The immediate containing node, or null when this node is a Root Node. Not nested further here.
    val parent: MpsNodeParentJson? = null,
)

/**
 * One node on a target node's containment chain: its immediate parent, and, when a full ancestry chain was requested,
 * that parent's own [parent] recursively up to the Root Node.
 */
@Serializable
data class MpsNodeParentJson(
    val type: String,
    // Containment role by which the node directly below this one (the node whose parent this is) sits in it. Present in
    // practice for every parent; nullable only to tolerate a node with no resolvable containment link.
    val role: String? = null,
    val name: String? = null,
    val concept: String,
    // False when the parent's concept could not be resolved (its language is not loaded); omitted on the happy path.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conceptValid: Boolean = true,
    val reference: String,
    // The next node up the containment chain. Populated only when a full ancestry chain was requested; null at the Root
    // Node and when only the immediate parent was requested.
    val parent: MpsNodeParentJson? = null,
)
