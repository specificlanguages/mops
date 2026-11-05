package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.TestResult
import com.specificlanguages.mops.protocol.TestRunReport
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
    private val events by lazy { directory.resolve("events").createDirectories() }
    private val reportPath by lazy { directory.resolve("report.json") }
    private val report by lazy { TestReportStore(reportPath) { now } }
    private val limits = TestRunLimits(cancellationPath = null, executionTimeoutMillis = 1) { now }
    private val collector by lazy { WorkerEventCollector(events, report, limits) }
    private var published = 0

    private fun publish(vararg properties: Pair<String, String>) {
        events.resolve("%08d.properties".format(published++)).writeText(properties.joinToString("\n") { (key, value) -> "$key=$value" })
    }

    @Test
    fun `discovering tests starts the execution phase and its time limit`() {
        report.phase("DISCOVERY")
        publish("discovered" to "1")
        collector.collect()
        assertEquals(
            TestRunReport(reportPath.toAbsolutePath().toString(), phase = "EXECUTION", discovered = 1, timingsMillis = mapOf("execution" to 0L)),
            report.snapshot(),
        )
        now = 2_000_000
        assertEquals("TIMED_OUT", limits.interruption())
    }

    @Test
    fun `discovering no tests stays in discovery and does not start the time limit`() {
        report.phase("DISCOVERY")
        collector.collect()
        publish("discovered" to "0")
        collector.collect()
        assertEquals(TestRunReport(reportPath.toAbsolutePath().toString(), phase = "DISCOVERY", discovered = 0), report.snapshot())
        now = 2_000_000
        assertNull(limits.interruption())
    }

    @Test
    fun `test results are recorded in publication order`() {
        publish("discovered" to "2")
        publish("id" to "b", "name" to "second", "kind" to "TEST", "status" to "FAILED")
        publish("id" to "a", "name" to "first", "kind" to "TEST", "status" to "PASSED")
        collector.collect()
        assertEquals(listOf(TestResult("b", "second", "TEST", "FAILED"), TestResult("a", "first", "TEST", "PASSED")), report.snapshot().results)
    }

    @Test
    fun `the finished event marks the worker finished and times the Ant shutdown`() {
        publish("discovered" to "1")
        collector.collect()
        assertFalse(collector.finished)
        now = 2_000_000
        publish("finished" to "true")
        collector.collect()
        assertTrue(collector.finished)
        now = 5_000_000
        report.endTiming()
        assertEquals(mapOf("execution" to 2L, "antShutdown" to 3L), report.snapshot().timingsMillis)
    }
}
