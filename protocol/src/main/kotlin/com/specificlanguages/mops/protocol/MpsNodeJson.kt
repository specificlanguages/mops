@file:OptIn(ExperimentalSerializationApi::class)

package com.specificlanguages.mops.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class MpsNodeJson(
    val model: String? = null,
    val role: String? = null,
    val concept: String,
    // False when the node's concept could not be resolved (its language is not loaded); omitted on the happy path.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conceptValid: Boolean = true,
    val id: String? = null,
    val properties: List<MpsNodePropertyJson>? = null,
    val references: List<MpsNodeReferenceJson>? = null,
    val children: List<MpsNodeJson>? = null,
)

@Serializable
data class MpsNodePropertyJson(
    val name: String,
    val value: String,
)

@Serializable
data class MpsNodeReferenceJson(
    val role: String,
    val target: MpsNodeReferenceTargetJson,
)

@Serializable
data class MpsNodeReferenceTargetJson(
    val model: String? = null,
    val node: String? = null,
    val name: String? = null,
    val concept: String? = null,
    // False when the reference could not be resolved to a target node; omitted when the target resolved.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val resolved: Boolean = true,
    // False when the target resolved but its concept could not be (its language is not loaded); omitted otherwise.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conceptValid: Boolean = true,
)
