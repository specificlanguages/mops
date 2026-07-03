package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import picocli.CommandLine.*

/**
 * Stops known daemon processes and removes stale daemon records.
 *
 * Like status, stop works from persisted records and does not require an MPS home. A failed stop attempt is treated as a
 * stale process because the record is no longer useful for future autostart decisions.
 */
@Command(name = "stop", description = ["Stop a daemon process."])
class DaemonStopCommand : CliCommand() {
    @ParentCommand
    lateinit var parent: DaemonOperations

    @Option(names = ["--all"], description = ["Stop daemons for all projects."])
    var all: Boolean = false

    override fun run() {
        val root = parent.root
        val pool = root.ensureDaemonPool()

        val recordSpec =
            if (all) DaemonPool.Spec.All
            else DaemonPool.Spec.ForProject(root.resolveProjectPath())

        val selected = pool.findRecords(recordSpec)

        if (selected.isEmpty()) {
            println("no mops daemons")
        } else {
            selected.forEach { storedRecord: StoredDaemonRecord ->
                val record = storedRecord.record
                if (pool.stop(storedRecord)) {
                    println("stopped project=${record.context.realProjectPath} pid=${record.pid}")
                } else {
                    println("removed stale daemon record for project=${record.context.realProjectPath}")
                }
            }
        }
    }
}
