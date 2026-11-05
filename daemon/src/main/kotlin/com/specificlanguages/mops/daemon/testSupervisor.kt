package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread

/**
 * Starts the Ant test worker of one test run and supervises it: records the worker's events in the report, enforces the
 * run's cancellation and time limit, and saves an incomplete report when the daemon dies. Starts no MPS environment.
 */
fun main(args: Array<String>) {
    val request = ProtocolJson.decodeRequest(Files.readString(Path.of(args[0]))) as TestRunRequest
    val report = TestReportStore(Path.of(args[1]))
    // The daemon owns the write end of this pipe. EOF also detects a killed parent that remains a zombie.
    thread(name = "test-worker-parent", isDaemon = true) {
        while (System.`in`.read() != -1) { }
        report.finish(TestRunConclusion("PARENT_TERMINATED", complete = false))
        destroyProcessTree(ProcessHandle.current())
        Runtime.getRuntime().halt(1)
    }
    val limits = TestRunLimits(request.cancellationPath?.let(Path::of), request.timeoutMillis)
    thread(name = "test-worker-watchdog", isDaemon = true) {
        val reason = requireNotNull(limits.watch(running = { true }))
        // Record the interruption before killing the Ant JVM, so the execution thread cannot finish the run first.
        report.finish(TestRunConclusion(reason, complete = false))
        destroyProcessTree(ProcessHandle.current())
        Runtime.getRuntime().halt(1)
    }
    try {
        // The daemon resolved the selection, so the worker sees the repository and path macros the daemon loaded.
        val selection = AntTestSelection.load(Path.of(args[4]))
        AntTestExecution(Path.of(args[3]), Path.of(args[1]).parent, Path.of(args[5]), report, limits).run(Path.of(args[2]), selection)
    } catch (failure: Throwable) {
        failure.printStackTrace()
        val phase = report.snapshot().phase
        val outcome = if (phase == "EXECUTION") "WORKER_FAILED" else "DISCOVERY_FAILED"
        report.finish(TestRunConclusion(outcome, complete = phase != "EXECUTION", listOf(failure.stackTraceToString())))
    }
    Runtime.getRuntime().halt(if (report.snapshot().successful) 0 else 1)
}
