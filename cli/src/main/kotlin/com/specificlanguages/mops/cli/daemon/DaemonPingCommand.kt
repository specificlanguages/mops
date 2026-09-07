package com.specificlanguages.mops.cli.daemon

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.protocol.DaemonResponse
import picocli.CommandLine.Command

/**
 * CLI command that proves daemon startup and protocol compatibility for the current MPS project.
 */
@Command(name = "ping", description = ["Start or reuse a project daemon and exchange a ping request."])
class DaemonPingCommand(private val environment: CommandEnvironment) : CliCommand() {
    override fun run() {
        val response = environment.daemon().ping()
        println(renderJson(response))
    }
}
