package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * Reads persisted daemon records and reports which project daemons are known locally.
 *
 * Status is intentionally record-based: it does not start a daemon or require an MPS home.
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
            println(
                "running workspace=${record.workspace} context=${record.context} port=${record.port} pid=${record.pid}",
            )
        }
    }
}
