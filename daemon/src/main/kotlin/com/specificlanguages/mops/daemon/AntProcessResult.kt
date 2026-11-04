package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.TestRunReport
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.useLines

/**
 * How the Ant process of a test run ended: whether the worker published its finished event, and the Ant exit code.
 * [antLog] is the Ant process output and [ideaLog] the test worker's IDE log.
 */
class AntProcessResult(
    private val finished: Boolean,
    private val exitCode: Int,
    private val antLog: Path,
    private val ideaLog: Path,
) {
    /**
     * Concludes a run whose report is [snapshot], naming the Test Selection by [selectionDescription] when the run
     * discovered no tests.
     */
    fun conclude(snapshot: TestRunReport, selectionDescription: String): TestRunConclusion {
        val conclusion = when {
            finished && snapshot.discovered == 0 ->
                TestRunConclusion("DISCOVERY_FAILED", complete = true, listOf("No tests discovered for selection: $selectionDescription"))
            finished && snapshot.hasFailedTests -> TestRunConclusion("TEST_FAILED", complete = true)
            finished && exitCode != 0 -> TestRunConclusion("WORKER_FAILED", complete = true, listOf(workerExitDiagnostic()))
            finished -> TestRunConclusion("SUCCESS", complete = true)
            snapshot.phase == "EXECUTION" -> TestRunConclusion("WORKER_FAILED", complete = false, listOf(workerExitDiagnostic()))
            else -> TestRunConclusion("DISCOVERY_FAILED", complete = true, listOf(workerExitDiagnostic()))
        }
        return if (conclusion.outcome == "SUCCESS") conclusion
            else conclusion.copy(diagnostics = conclusion.diagnostics + listOfNotNull(classLoadingDiagnostic()))
    }

    private fun workerExitDiagnostic() = "Ant test worker exited $exitCode; see $antLog\n${antLog.readText().takeLast(MAX_ANT_LOG_CHARS)}"

    /**
     * Summarizes the worker's warnings about modules it marked invalid for class loading, which otherwise surface only as
     * a ClassNotFoundException for the test classes of a dependent module. Null when the worker logged none.
     */
    private fun classLoadingDiagnostic(): String? {
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
