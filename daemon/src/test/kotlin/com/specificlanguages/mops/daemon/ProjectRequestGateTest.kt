package com.specificlanguages.mops.daemon

import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.*

class ProjectRequestGateTest {
    @Test
    fun `read batch shares refresh and exclusive work separates later readers`() {
        val refreshing = CountDownLatch(1)
        val finishRefresh = CountDownLatch(1)
        val readers = CountDownLatch(2)
        val finishReads = CountDownLatch(1)
        val writing = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val refreshes = AtomicInteger()
        val gate = ProjectRequestGate {
            if (refreshes.incrementAndGet() == 1) {
                refreshing.countDown()
                finishRefresh.awaitChecked()
            }
        }
        val first = task { gate.run(true) { readers.countDown(); finishReads.awaitChecked() } }
        try {
            refreshing.awaitChecked()
            val second = task { gate.run(true) { readers.countDown(); finishReads.awaitChecked() } }
            second.awaitQueued()
            val exclusive = task { gate.run(false) { writing.countDown(); finishWrite.awaitChecked() } }
            exclusive.awaitQueued()
            val later = task { gate.run(true) { refreshes.get() } }
            later.awaitQueued()
            finishRefresh.countDown()
            readers.awaitChecked()
            assertEquals(1, refreshes.get())
            assertEquals(1, writing.count)
            assertFalse(later.future.isDone)
            finishReads.countDown()
            writing.awaitChecked()
            assertEquals(2, refreshes.get())
            assertFalse(later.future.isDone)
            finishWrite.countDown()
            assertEquals(3, later.result())
            first.result()
            second.result()
            exclusive.result()
        } finally {
            finishRefresh.countDown()
            finishReads.countDown()
            finishWrite.countDown()
        }
    }

    @Test
    fun `a reader arriving during active reads waits for its own refresh`() {
        val reading = CountDownLatch(1)
        val finishRead = CountDownLatch(1)
        val refreshes = AtomicInteger()
        val gate = ProjectRequestGate { refreshes.incrementAndGet() }
        val first = task { gate.run(true) { reading.countDown(); finishRead.awaitChecked() } }
        try {
            reading.awaitChecked()
            val second = task { gate.run(true) { refreshes.get() } }
            second.awaitQueued()
            assertEquals(1, refreshes.get())
            finishRead.countDown()
            assertEquals(2, second.result())
            first.result()
        } finally {
            finishRead.countDown()
        }
    }

    @Test
    fun `refresh failure is shared by its batch and releases admission`() {
        val refreshing = CountDownLatch(1)
        val finishRefresh = CountDownLatch(1)
        val refreshes = AtomicInteger()
        val failure = IllegalStateException("refresh failed")
        val gate = ProjectRequestGate {
            if (refreshes.incrementAndGet() == 1) {
                refreshing.countDown()
                finishRefresh.awaitChecked()
                throw failure
            }
        }
        val first = task { runCatching { gate.run(true) { fail("must not run") } }.exceptionOrNull() }
        try {
            refreshing.awaitChecked()
            val second = task { runCatching { gate.run(true) { fail("must not run") } }.exceptionOrNull() }
            second.awaitQueued()
            val exclusive = task { gate.run(false) { "recovered" } }
            exclusive.awaitQueued()
            finishRefresh.countDown()
            assertSame(failure, first.result())
            assertSame(failure, second.result())
            assertEquals("recovered", exclusive.result())
            assertEquals(2, refreshes.get())
        } finally {
            finishRefresh.countDown()
        }
    }

    @Test
    fun `operation failure releases admission for queued work`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gate = ProjectRequestGate {}
        val first = task {
            runCatching { gate.run(false) { entered.countDown(); release.awaitChecked(); error("failed") } }
        }
        try {
            entered.awaitChecked()
            val next = task { gate.run(true) { "finished" } }
            next.awaitQueued()
            release.countDown()
            assertEquals("failed", first.result().exceptionOrNull()?.message)
            assertEquals("finished", next.result())
        } finally {
            release.countDown()
        }
    }

    private fun <T> task(block: () -> T): Task<T> {
        val future = FutureTask(block)
        return Task(future, thread(isDaemon = true) { future.run() })
    }

    private class Task<T>(val future: FutureTask<T>, val thread: Thread) {
        fun result(): T = future.get(10, TimeUnit.SECONDS)

        // Each follower has only one blocking point: the gate condition. Observe it before advancing the leader.
        fun awaitQueued() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (thread.state != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals(Thread.State.WAITING, thread.state)
        }
    }

    private fun CountDownLatch.awaitChecked() = assertTrue(await(10, TimeUnit.SECONDS), "latch timed out")
}
