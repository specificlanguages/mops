package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.TestRunReport
import java.nio.file.Path

/**
 * The IntelliJ system directory (VFS, indexes, caches) at [path] that the test worker keeps between runs, so that it
 * does not scan and index the whole MPS distribution again each time.
 *
 * One daemon serves a project and runs one test operation at a time, so no other run uses the directory at the same
 * time. When [deleteAfterUncleanRun] is set, a run that does not end cleanly deletes it, so the next run starts with a
 * fresh one instead of with storages a killed or crashed worker may have left behind. A run ends uncleanly when its
 * report is incomplete (timed out, cancelled, a worker killed during execution) or the worker failed (`WORKER_FAILED`).
 */
class TestWorkerSystemDirectory(val path: Path, private val deleteAfterUncleanRun: Boolean) {
    /** Deletes the directory after the run of [report] when the run did not end cleanly and this directory allows it. */
    fun afterRun(report: TestRunReport) {
        if (deleteAfterUncleanRun && (!report.complete || report.outcome == "WORKER_FAILED")) {
            path.toFile().deleteRecursively()
        }
    }
}
