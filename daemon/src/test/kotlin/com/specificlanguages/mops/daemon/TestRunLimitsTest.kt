package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestRunLimitsTest {
    @TempDir
    lateinit var directory: Path

    private var now = 0L

    private fun limits(timeoutMillis: Long, cancellation: Path? = null) = TestRunLimits(cancellation, timeoutMillis) { now }

    @Test
    fun `the time limit counts from the start of test execution`() {
        val limits = limits(timeoutMillis = 10_000)

        now = TimeUnit.MINUTES.toNanos(5) // startup, build, and discovery
        assertNull(limits.interruption())

        limits.executionStarted()
        now += TimeUnit.MILLISECONDS.toNanos(9_999)
        assertNull(limits.interruption())

        now += TimeUnit.MILLISECONDS.toNanos(1)
        assertEquals("TIMED_OUT", limits.interruption())
    }

    @Test
    fun `a later execution start does not restart the time limit`() {
        val limits = limits(timeoutMillis = 10_000)
        limits.executionStarted()
        now += TimeUnit.SECONDS.toNanos(6)
        limits.executionStarted()
        now += TimeUnit.SECONDS.toNanos(4)

        assertEquals("TIMED_OUT", limits.interruption())
    }

    @Test
    fun `a zero time limit never times out`() {
        val limits = limits(timeoutMillis = 0)
        limits.executionStarted()
        now += TimeUnit.DAYS.toNanos(1)

        assertNull(limits.interruption())
    }

    @Test
    fun `cancellation stops the run before and during execution`() {
        val cancel = directory.resolve("cancel")
        val limits = limits(timeoutMillis = 10_000, cancellation = cancel)
        assertNull(limits.interruption())

        cancel.writeText("cancel")
        assertEquals("CANCELLED", limits.interruption())

        limits.executionStarted()
        now += TimeUnit.MINUTES.toNanos(1)
        assertEquals("CANCELLED", limits.interruption())
    }

    @Test
    fun `watch returns the interruption while running and null once the process is gone`() {
        val limits = limits(timeoutMillis = 1)
        limits.executionStarted()
        now += TimeUnit.SECONDS.toNanos(1)

        assertEquals("TIMED_OUT", limits.watch(running = { true }, pollMillis = 1))
        assertNull(limits.watch(running = { false }, pollMillis = 1))
    }

    @Test
    fun `destroying a process tree kills the process and its descendants`() {
        val process = ProcessBuilder("sh", "-c", "sleep 60 & wait").start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (process.descendants().count() == 0L && System.nanoTime() < deadline) Thread.sleep(10)
            val child = process.descendants().toList().single()

            destroyProcessTree(process.toHandle())

            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "the process must be killed")
            assertTrue(child.onExit().get(10, TimeUnit.SECONDS) != null)
            assertFalse(child.isAlive, "its descendant must be killed")
        } finally {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }
}
