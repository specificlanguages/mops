package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.TestResult
import com.specificlanguages.mops.protocol.TestRunReport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class AntProcessResultTest {
    @TempDir
    lateinit var directory: Path

    private val antLog get() = directory.resolve("ant.log")
    private val ideaLog get() = directory.resolve("idea.log")

    @BeforeEach
    fun writeLogs() {
        antLog.writeText("BUILD FAILED")
        ideaLog.writeText("2026-01-01 12:00:00,000 [   1000]   WARN - #jetbrains.mps.classloading.ModulesWatcher - Module library is invalid\n")
    }

    private fun report(phase: String, discovered: Int, vararg statuses: String) = TestRunReport(
        "report.json", phase = phase, discovered = discovered,
        results = statuses.mapIndexed { index, status -> TestResult("test$index", "test$index", "TEST", status) },
    )

    private fun conclude(report: TestRunReport, finished: Boolean, exitCode: Int) =
        AntProcessResult(finished, exitCode, antLog, ideaLog).conclude(report, "model tests")

    private val workerExited get() = "Ant test worker exited 1; see $antLog\nBUILD FAILED"
    private val classLoading get() = "The test worker could not load classes of some modules; see $ideaLog\n  Module library is invalid"

    @Test
    fun `a finished run with passing tests succeeds without diagnostics`() {
        assertEquals(TestRunConclusion("SUCCESS", complete = true), conclude(report("EXECUTION", 1, "PASSED"), finished = true, exitCode = 0))
    }

    @Test
    fun `a finished run that discovered no tests is a discovery failure naming the selection`() {
        assertEquals(
            TestRunConclusion("DISCOVERY_FAILED", complete = true, listOf("No tests discovered for selection: model tests", classLoading)),
            conclude(report("DISCOVERY", 0), finished = true, exitCode = 1),
        )
    }

    @Test
    fun `a finished run with a failed test is a test failure regardless of the Ant exit code`() {
        assertEquals(
            TestRunConclusion("TEST_FAILED", complete = true, listOf(classLoading)),
            conclude(report("EXECUTION", 2, "PASSED", "FAILED"), finished = true, exitCode = 1),
        )
    }

    @Test
    fun `a finished run whose Ant process failed without failed tests is a worker failure`() {
        assertEquals(
            TestRunConclusion("WORKER_FAILED", complete = true, listOf(workerExited, classLoading)),
            conclude(report("EXECUTION", 1, "PASSED"), finished = true, exitCode = 1),
        )
    }

    @Test
    fun `a run that ended during execution is an incomplete worker failure`() {
        assertEquals(
            TestRunConclusion("WORKER_FAILED", complete = false, listOf(workerExited, classLoading)),
            conclude(report("EXECUTION", 2, "PASSED"), finished = false, exitCode = 1),
        )
    }

    @Test
    fun `a run that ended before execution is a discovery failure`() {
        assertEquals(
            TestRunConclusion("DISCOVERY_FAILED", complete = true, listOf(workerExited, classLoading)),
            conclude(report("DISCOVERY", 0), finished = false, exitCode = 1),
        )
    }
}
