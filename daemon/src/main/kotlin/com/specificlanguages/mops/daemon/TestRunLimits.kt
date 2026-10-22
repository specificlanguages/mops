package com.specificlanguages.mops.daemon

import java.nio.file.Files
import java.nio.file.Path

/**
 * Decides when a test run must stop early: when the cancellation file appears, or when test execution has run longer
 * than [executionTimeoutMillis] (0 disables the limit). The limit counts from [executionStarted], so startup, build, and
 * discovery do not use it up.
 */
class TestRunLimits(
    private val cancellationPath: Path?,
    private val executionTimeoutMillis: Long,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    @Volatile private var executionStartedNanos: Long? = null

    fun executionStarted() {
        if (executionStartedNanos == null) executionStartedNanos = nanoTime()
    }

    /** The outcome to record if the run must stop now, or null. */
    fun interruption(): String? {
        if (cancellationPath != null && Files.exists(cancellationPath)) return "CANCELLED"
        val started = executionStartedNanos ?: return null
        return if (executionTimeoutMillis != 0L && (nanoTime() - started) / 1_000_000 >= executionTimeoutMillis) "TIMED_OUT" else null
    }

    /** Polls until [running] returns false, then returns null, or until an interruption occurs, then returns it. */
    fun watch(running: () -> Boolean, pollMillis: Long = 100): String? {
        while (running()) {
            interruption()?.let { return it }
            Thread.sleep(pollMillis)
        }
        return null
    }
}

/** Kills [process]'s descendants, deepest first, then [process] itself unless it is the current process. */
fun destroyProcessTree(process: ProcessHandle) {
    process.descendants().toList().asReversed().forEach { it.destroyForcibly() }
    if (process != ProcessHandle.current()) process.destroyForcibly()
}
