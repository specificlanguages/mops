package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.TestRunReport
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.useLines

/**
 * How the Ant process of a test run in [directory] ended: whether the worker published its finished event, and the Ant
 * exit code. [finish] maps it to the report's outcome, completeness, and diagnostics.
 */
class AntProcessResult(private val finished: Boolean, private val exitCode: Int, private val directory: Path) {
    /** Finishes [report], naming the Test Selection by [selectionDescription] when the run discovered no tests. */
    fun finish(report: TestReportStore, selectionDescription: String) {
        val snapshot = report.snapshot()
        val outcome = outcome(snapshot)
        val runDiagnostic = when {
            finished && snapshot.discovered == 0 -> "No tests discovered for selection: $selectionDescription"
            !finished || exitCode != 0 && snapshot.results.none { it.status == "FAILED" } -> workerExitDiagnostic()
            else -> null
        }
        val classLoading = if (outcome == "SUCCESS") null else classLoadingDiagnostic(directory.resolve("test-log/idea.log"))
        report.finish(TestRunConclusion(outcome, complete = finished || snapshot.phase != "EXECUTION", listOfNotNull(runDiagnostic, classLoading)))
    }

    private fun outcome(snapshot: TestRunReport): String = when {
        finished && snapshot.discovered == 0 -> "DISCOVERY_FAILED"
        finished && snapshot.results.any { it.status == "FAILED" } -> "TEST_FAILED"
        finished && exitCode != 0 -> "WORKER_FAILED"
        finished -> "SUCCESS"
        snapshot.phase == "EXECUTION" -> "WORKER_FAILED"
        else -> "DISCOVERY_FAILED"
    }

    private fun workerExitDiagnostic(): String {
        val antLog = directory.resolve("ant.log")
        return "Ant test worker exited $exitCode; see $antLog\n${antLog.readText().takeLast(MAX_ANT_LOG_CHARS)}"
    }

    /**
     * Summarizes the worker's warnings about modules it marked invalid for class loading, which otherwise surface only as
     * a ClassNotFoundException for the test classes of a dependent module. Null when the worker logged none.
     */
    private fun classLoadingDiagnostic(ideaLog: Path): String? {
        if (!ideaLog.exists()) return null
        val warnings = ideaLog.useLines { lines -> lines.mapNotNull { CLASS_LOADING_WARNING.find(it)?.groupValues?.get(1) }.toList() }
        if (warnings.isEmpty()) return null
        val shown = warnings.take(MAX_CLASS_LOADING_WARNINGS)
        return (listOf("The test worker could not load classes of some modules; see $ideaLog") + shown.map { "  $it" } +
            listOfNotNull((warnings.size - shown.size).takeIf { it > 0 }?.let { "  ... $it more" })).joinToString("\n")
    }

    private companion object {
        val CLASS_LOADING_WARNING = Regex("""\bWARN - #?jetbrains\.mps\.classloading\.ModulesWatcher - (.*)""")
        const val MAX_CLASS_LOADING_WARNINGS = 20
        const val MAX_ANT_LOG_CHARS = 16000
    }
}
