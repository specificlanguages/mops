package com.specificlanguages.mops.daemon

/**
 * How a Test Run ends: its [outcome], whether the Test Run Report is [complete], and the [diagnostics] to add to the
 * report. [TestReportStore.finish] records it.
 */
data class TestRunConclusion(val outcome: String, val complete: Boolean, val diagnostics: List<String> = emptyList())
