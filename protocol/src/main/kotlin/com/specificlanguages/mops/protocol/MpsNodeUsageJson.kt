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
)
