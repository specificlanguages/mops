package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.TestRunReport
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class TestWorkerSystemDirectoryTest {
    @TempDir
    lateinit var tempDir: Path

    private fun keptAfter(report: TestRunReport, deleteAfterUncleanRun: Boolean = true): Boolean {
        val path = tempDir.resolve("test-worker-system").createDirectories()
        path.resolve("caches").createDirectories().resolve("records.dat").writeText("records")
        TestWorkerSystemDirectory(path, deleteAfterUncleanRun).afterRun(report)
        return path.exists()
    }

    @Test
    fun `a run that does not end cleanly deletes the directory`() {
        val outcomes = listOf("TIMED_OUT", "CANCELLED", "WORKER_FAILED", "PARENT_TERMINATED")
        val kept = outcomes.map { keptAfter(TestRunReport("report.json", it, complete = false)) }
        assertEquals(outcomes.map { false }, kept)
    }

    @Test
    fun `a worker that exits with an error after its last event deletes the directory`() {
        assertEquals(false, keptAfter(TestRunReport("report.json", "WORKER_FAILED", complete = true)))
    }

    @Test
    fun `a run that ends cleanly keeps the directory, even when tests fail`() {
        val outcomes = listOf("SUCCESS", "TEST_FAILED", "DISCOVERY_FAILED")
        val kept = outcomes.map { keptAfter(TestRunReport("report.json", it, complete = true)) }
        assertEquals(outcomes.map { true }, kept)
    }

    @Test
    fun `a directory the daemon must not delete outlives an unclean run`() {
        assertEquals(true, keptAfter(TestRunReport("report.json", "TIMED_OUT", complete = false), deleteAfterUncleanRun = false))
    }
}
