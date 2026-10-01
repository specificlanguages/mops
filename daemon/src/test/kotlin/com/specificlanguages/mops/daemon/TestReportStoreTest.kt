package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class TestReportStoreTest {
    @TempDir
    lateinit var directory: Path

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
