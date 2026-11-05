package com.specificlanguages.mops.daemon

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * FIFO admission outside MPS model actions, excluding refreshes and exclusive work from active readers. Only requests
 * queued before a refresh starts are admitted after it, so every request sees the external changes made before it
 * arrived.
 */
internal class ProjectRequestGate(private val refresh: () -> Unit) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val waiting = ArrayDeque<Ticket>()
    private var active = 0
    private var refreshing = false
    private var queued = 0L

    fun <T> run(parallelRead: Boolean, operation: () -> T): T {
        val ticket = lock.withLock {
            val ticket = Ticket(parallelRead, ++queued)
            waiting.addLast(ticket)
            while (!ticket.admitted) {
                if (active == 0 && !refreshing && waiting.first() === ticket) {
                    refreshing = true
                    val queuedBeforeRefresh = queued
                    lock.unlock()
                    val failure = try {
                        refresh()
                        null
                    } catch (throwable: Throwable) {
                        throwable
                    } finally {
                        lock.lock()
                    }
                    // Readers queued before the refresh started share its result. The first exclusive ticket and the
                    // first ticket queued during the refresh end the batch.
                    do {
                        val next = waiting.removeFirst()
                        next.failure = failure
                        next.admitted = true
                        active++
                    } while (parallelRead && waiting.firstOrNull()?.sharesRefresh(queuedBeforeRefresh) == true)
                    refreshing = false
                    changed.signalAll()
                } else {
                    changed.awaitUninterruptibly()
                }
            }
            ticket
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

    private class Ticket(val parallelRead: Boolean, val number: Long) {
        fun sharesRefresh(queuedBeforeRefresh: Long) = parallelRead && number <= queuedBeforeRefresh

        var admitted = false
        var failure: Throwable? = null
    }
}
