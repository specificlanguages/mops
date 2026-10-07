package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class TestReportStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `phase timings survive updates interruptions and a new process clock`() {
        var now = 0L
        val path = directory.resolve("timings.json")
        val report = TestReportStore(path) { now }
        report.beginTiming("startup")
        now = 1_500_000
        report.phase("DISCOVERY")
        now = 3_000_000
        report.beginTiming("execution")
        now = 8_000_000
        report.finish("TIMED_OUT", false)
        now = 100_000_000
        report.finish("SUCCESS", true)
        val saved = TestReportStore(path) { 0L }
        assertEquals(mapOf("startup" to 3L, "execution" to 5L), saved.snapshot().timingsMillis)
        assertEquals("TIMED_OUT", saved.snapshot().outcome)
        saved.recordTiming("workerLifetime", 12)
        assertEquals(mapOf("startup" to 3L, "execution" to 5L, "workerLifetime" to 12L),
            TestReportStore(path).snapshot().timingsMillis)
    }

    @Test
    fun `interruption outcome survives late completion and retains test events`() {
        for (outcome in listOf("CANCELLED", "TIMED_OUT", "PARENT_TERMINATED")) {
            val path = directory.resolve("$outcome.json")
            val report = TestReportStore(path)
            report.finish(outcome, false)
            report.event(mapOf("id" to "test", "name" to "completed", "kind" to "TEST", "status" to "PASSED"))
            val interrupted = report.snapshot()
            report.finish("WORKER_FAILED", false, "Ant process terminated")
            report.finish("SUCCESS", true)
            assertEquals(interrupted, TestReportStore(path).snapshot())
        }
    }
}
