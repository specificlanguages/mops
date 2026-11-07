package com.specificlanguages.mops.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists

/**
 * Reads the numbered protocol event files the test worker publishes in [events] and records them in [report]. A
 * discovery event with tests starts the execution phase and the execution time limit.
 */
class WorkerEventCollector(private val events: Path, private val report: TestReportStore, private val limits: TestRunLimits) {
    private var next = 0

    /** Whether the worker has published its finished event. */
    var finished = false
        private set

    /** Records the events published since the previous call. */
    fun collect() {
        while (true) {
            val path = events.resolve("%08d.properties".format(next))
            if (!path.exists()) break
            val event = Properties().also { properties -> Files.newInputStream(path).use { properties.load(it) } }
            next++
            when {
                event.containsKey("finished") -> {
                    finished = true
                    report.beginTiming("antShutdown")
                }
                event.containsKey("discovered") -> discovered(event.getProperty("discovered").toInt())
                else -> report.event(event.stringPropertyNames().associateWith { event.getProperty(it) })
            }
        }
    }

    private fun discovered(count: Int) {
        report.event(mapOf("discovered" to count))
        report.beginTiming("execution")
        if (count > 0) {
            report.phase("EXECUTION")
            limits.executionStarted()
        }
    }
}
