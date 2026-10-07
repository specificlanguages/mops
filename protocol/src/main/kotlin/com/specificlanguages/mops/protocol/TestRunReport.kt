package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable
data class TestRunReport(
    val reportPath: String,
    val outcome: String = "RUNNING",
    val phase: String = "STARTUP",
    val complete: Boolean = false,
    val discovered: Int = 0,
    val results: List<TestResult> = emptyList(),
    val diagnostics: List<String> = emptyList(),
    val build: MakeResponse? = null,
    val timingsMillis: Map<String, Long> = emptyMap(),
) {
    val successful: Boolean get() = complete && outcome == "SUCCESS"
}
