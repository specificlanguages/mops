package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable
data class InstanceDifferenceJson(
    val layer: String,
    val missingCount: Int,
    val missing: List<String>,
    val unexpectedCount: Int,
    val unexpected: List<String>,
)
