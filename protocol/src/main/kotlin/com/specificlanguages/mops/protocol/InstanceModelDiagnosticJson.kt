package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable
data class InstanceModelDiagnosticJson(
    val model: String,
    val implementation: String,
    val source: String,
    val sourceType: String,
    val streams: List<String>,
    val readOnly: Boolean,
    val changed: Boolean,
    val loadedBefore: Boolean,
    val facadeCount: Int?,
    val lookupCount: Int?,
    val queryTreeCount: Int?,
    val semanticTreeCount: Int?,
    val differences: List<InstanceDifferenceJson>,
    val errors: List<String>,
)
