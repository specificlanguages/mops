package com.specificlanguages.mops.cli.daemon

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * Probes known daemons without starting them or requiring an MPS home. Unreachable records are retained.
 */
@Command(name = "status", description = ["Print daemon status."])
class DaemonStatusCommand(private val environment: CommandEnvironment) : CliCommand() {
    @Option(names = ["--all"], description = ["Show daemon state for all projects."])
    var all: Boolean = false

    override fun run() {
        val pool = environment.daemonPool()

        val recordSpec =
            if (all) DaemonPool.Spec.All
            else DaemonPool.Spec.ForProject(environment.projectPath())

        val selected = pool.findRecords(recordSpec)

        if (selected.isEmpty()) {
            println("no mops daemons")
            return
        }

        selected.forEach { storedRecord: StoredDaemonRecord ->
            val record = storedRecord.record
            val state = if (pool.isReachable(record)) "running" else "unreachable"
            println(
                "$state workspace=${record.workspace} context=${record.context} port=${record.port} pid=${record.pid}",
            )
        }
    }
}
