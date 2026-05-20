package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

/**
 * Reads persisted daemon records and reports which project daemons are known locally.
 *
 * Status is intentionally record-based: it does not start a daemon or require an MPS home.
 */
@Command(name = "status", description = ["Print daemon status."])
class DaemonStatusCommand : Runnable {
    @ParentCommand
    lateinit var parent: DaemonOperations

    @Spec
    lateinit var spec: CommandSpec

    @Option(names = ["--all"], description = ["Show daemon state for all projects."])
    var all: Boolean = false

    override fun run() {
        val root = parent.root
        val pool = root.ensureDaemonPool()

        val recordSpec =
            if (all) DaemonPool.Spec.All
            else DaemonPool.Spec.ForProject(root.resolveProjectPath())

        val selected = pool.findRecords(recordSpec)

        val out = spec.commandLine().out
        if (selected.isEmpty()) {
            out.println("no mops daemons")
            return
        }

        selected.forEach { storedRecord: StoredDaemonRecord ->
            val record = storedRecord.record
            out.println(
                "running workspace=${record.workspace} context=${record.context} port=${record.port} pid=${record.pid}",
            )
        }
    }
}
