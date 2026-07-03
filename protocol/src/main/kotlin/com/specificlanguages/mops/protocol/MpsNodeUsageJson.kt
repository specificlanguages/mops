package com.specificlanguages.mops.protocol

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
    val reference: String,
)
