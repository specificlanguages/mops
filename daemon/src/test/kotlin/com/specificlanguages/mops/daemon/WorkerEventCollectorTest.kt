package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.TestResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkerEventCollectorTest {
    @TempDir
    lateinit var directory: Path

    private var now = 0L
    private val events get() = directory.resolve("events").createDirectories()
    private val report by lazy { TestReportStore(directory.resolve("report.json")) { now } }
    private val limits = TestRunLimits(null, 1) { now }
    private var published = 0

    private fun publish(vararg properties: Pair<String, String>) {
        events.resolve("%08d.properties".format(published++)).writeText(properties.joinToString("\n") { (key, value) -> "$key=$value" })
    }

    @Test
    fun `discovered tests start execution and its time limit and results are recorded in order`() {
        report.phase("DISCOVERY")
        val collector = WorkerEventCollector(events, report, limits)
        publish("discovered" to "1")
        publish("id" to "a", "name" to "passes", "kind" to "TEST", "status" to "PASSED")
        collector.collect()
        assertEquals("EXECUTION", report.snapshot().phase)
        assertEquals(1, report.snapshot().discovered)
        assertEquals(listOf(TestResult("a", "passes", "TEST", "PASSED")), report.snapshot().results)
        assertFalse(collector.finished)
        now = 2_000_000
        assertEquals("TIMED_OUT", limits.interruption())

        publish("finished" to "true")
        collector.collect()
        assertTrue(collector.finished)
        now = 5_000_000
        report.endTiming()
        assertEquals(mapOf("execution" to 2L, "antShutdown" to 3L), report.snapshot().timingsMillis)
    }

    @Test
    fun `discovering no tests stays in discovery and does not start the time limit`() {
        report.phase("DISCOVERY")
        val collector = WorkerEventCollector(events, report, limits)
        collector.collect()
        publish("discovered" to "0")
        collector.collect()
        assertEquals("DISCOVERY", report.snapshot().phase)
        now = 2_000_000
        assertNull(limits.interruption())
    }
}
