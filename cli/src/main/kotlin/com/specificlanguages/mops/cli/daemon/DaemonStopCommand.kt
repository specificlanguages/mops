package com.specificlanguages.mops.cli.daemon

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.output.metadataLine
import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import picocli.CommandLine.*

/**
 * Stops known daemon processes and removes stale daemon records.
 *
 * Like status, stop works from persisted records and does not require an MPS home. A stop request that gets no response
 * is treated as a stale record and removed, because the record is no longer useful for future autostart decisions; the
 * recorded process is not signalled. A daemon that acknowledges the stop but survives the force-kill keeps its record
 * and makes the command exit non-zero.
 */
@Command(name = "stop", description = ["Stop a daemon process."])
class DaemonStopCommand(private val environment: CommandEnvironment) : CliCommand(), IExitCodeGenerator {
    @Option(names = ["--all"], description = ["Stop daemons for all projects."])
    var all: Boolean = false

    private var exitCode: Int = 0

    override fun run() {
        val pool = environment.daemonPool()

        val recordSpec =
            if (all) DaemonPool.Spec.All
            else DaemonPool.Spec.ForProject(environment.projectPath())

        val selected = pool.findRecords(recordSpec)

        if (selected.isEmpty()) {
            println(metadataLine("no mops daemons"))
        } else {
            selected.forEach { storedRecord: StoredDaemonRecord ->
                val record = storedRecord.record
                val project = record.context.realProjectPath
                when (pool.stop(storedRecord)) {
                    DaemonPool.StopOutcome.STOPPED ->
                        println("stopped project=$project pid=${record.pid}")

                    DaemonPool.StopOutcome.ALREADY_GONE ->
                        println(
                            "removed stale daemon record for project=$project; no response from pid=${record.pid}, " +
                                "process not signalled",
                        )

                    DaemonPool.StopOutcome.NOT_TERMINATED -> {
                        System.err.println(
                            "mops: could not stop project=$project: pid=${record.pid} is still running after a " +
                                "force-kill; its record is kept so the next daemon stop retries it",
                        )
                        exitCode = 1
                    }
                }
            }
        }
    }

    override fun getExitCode(): Int = exitCode
}
