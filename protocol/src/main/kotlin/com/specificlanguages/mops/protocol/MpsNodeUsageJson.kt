package com.specificlanguages.mops.protocol

data class MpsNodeUsageJson(
    val role: String,
    val owner: MpsNodeSummaryJson,
)

data class MpsNodeSummaryJson(
    val type: String,
    val name: String?,
    val concept: String,
    val reference: String,
)
