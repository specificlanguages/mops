package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class AntProcessResultTest {
    @TempDir
    lateinit var directory: Path

    private data class Finished(val outcome: String, val complete: Boolean, val diagnostics: List<String>)

    private val antLog get() = directory.resolve("ant.log")
    private val ideaLog get() = directory.resolve("test-log/idea.log")

    private fun report(phase: String, discovered: Int, vararg statuses: String): TestReportStore {
        antLog.writeText("BUILD FAILED")
        ideaLog.parent.createDirectories()
        ideaLog.writeText("2026-01-01 12:00:00,000 [   1000]   WARN - #jetbrains.mps.classloading.ModulesWatcher - Module library is invalid\n")
        val report = TestReportStore(directory.resolve("report.json"))
        report.phase(phase)
        report.event(mapOf("discovered" to discovered))
        statuses.forEachIndexed { index, status ->
            report.event(mapOf("id" to "test$index", "name" to "test$index", "kind" to "TEST", "status" to status))
        }
        return report
    }

    private fun finish(report: TestReportStore, finished: Boolean, exitCode: Int): Finished {
        AntProcessResult(finished, exitCode, directory).finish(report, "model tests")
        return report.snapshot().let { Finished(it.outcome, it.complete, it.diagnostics) }
    }

    private val workerExited get() = "Ant test worker exited 1; see $antLog\nBUILD FAILED"
    private val classLoading get() = "The test worker could not load classes of some modules; see $ideaLog\n  Module library is invalid"

    @Test
    fun `a finished run with passing tests succeeds without diagnostics`() {
        assertEquals(Finished("SUCCESS", true, emptyList()), finish(report("EXECUTION", 1, "PASSED"), finished = true, exitCode = 0))
    }

    @Test
    fun `a finished run that discovered no tests is a discovery failure naming the selection`() {
        assertEquals(
            Finished("DISCOVERY_FAILED", true, listOf("No tests discovered for selection: model tests", classLoading)),
            finish(report("DISCOVERY", 0), finished = true, exitCode = 1),
        )
    }

    @Test
    fun `a finished run with a failed test is a test failure regardless of the Ant exit code`() {
        assertEquals(Finished("TEST_FAILED", true, listOf(classLoading)), finish(report("EXECUTION", 2, "PASSED", "FAILED"), finished = true, exitCode = 1))
    }

    @Test
    fun `a finished run whose Ant process failed without failed tests is a worker failure`() {
        assertEquals(
            Finished("WORKER_FAILED", true, listOf(workerExited, classLoading)),
            finish(report("EXECUTION", 1, "PASSED"), finished = true, exitCode = 1),
        )
    }

    @Test
    fun `a run that ended during execution is an incomplete worker failure`() {
        assertEquals(
            Finished("WORKER_FAILED", false, listOf(workerExited, classLoading)),
            finish(report("EXECUTION", 2, "PASSED"), finished = false, exitCode = 1),
        )
    }

    @Test
    fun `a run that ended before execution is a discovery failure`() {
        assertEquals(
            Finished("DISCOVERY_FAILED", true, listOf(workerExited, classLoading)),
            finish(report("DISCOVERY", 0), finished = false, exitCode = 1),
        )
    }
}
