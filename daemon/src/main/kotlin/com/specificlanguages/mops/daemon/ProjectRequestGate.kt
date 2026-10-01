package com.specificlanguages.mops.daemon

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** FIFO admission outside MPS model actions, excluding refreshes and exclusive work from active readers. */
internal class ProjectRequestGate(private val refresh: () -> Unit) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val waiting = ArrayDeque<Ticket>()
    private var active = 0
    private var refreshing = false

    fun <T> run(parallelRead: Boolean, operation: () -> T): T {
        val ticket = Ticket(parallelRead)
        lock.withLock {
            waiting.addLast(ticket)
            while (!ticket.admitted) {
                if (active == 0 && !refreshing && waiting.first() === ticket) {
                    refreshing = true
                    lock.unlock()
                    val failure = try {
                        refresh()
                        null
                    } catch (throwable: Throwable) {
                        throwable
                    } finally {
                        lock.lock()
                    }
                    // Readers queued during refresh share its result. The first exclusive ticket ends the batch.
                    do {
                        val next = waiting.removeFirst()
                        next.failure = failure
                        next.admitted = true
                        active++
                    } while (parallelRead && waiting.firstOrNull()?.parallelRead == true)
                    refreshing = false
                    changed.signalAll()
                } else {
                    changed.awaitUninterruptibly()
                }
            }
        }
        try {
            ticket.failure?.let { throw it }
            return operation()
        } finally {
            lock.withLock {
                active--
                changed.signalAll()
            }
        }
    }

    private class Ticket(val parallelRead: Boolean) {
        var admitted = false
        var failure: Throwable? = null
    }
}
