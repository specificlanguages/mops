package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

/**
 * CLI command that proves daemon startup and protocol compatibility for the current MPS project.
 */
@Command(name = "ping", description = ["Start or reuse a project daemon and exchange a ping request."])
class DaemonPingCommand : Runnable {
    @ParentCommand
    lateinit var parent: DaemonOperations

    override fun run() {
        val root = parent.root
        val response = root.ensureDaemon().ping()
        println(ProtocolJson.encodeResponse(response))
    }
}
