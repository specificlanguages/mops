package com.specificlanguages.mops.cli.daemon

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.protocol.DaemonResponse
import picocli.CommandLine.Command

/**
 * CLI command that proves daemon startup and protocol compatibility for the current MPS project.
 */
@Command(
    name = "ping",
    header = ["Start or reuse a project daemon and exchange a ping request."],
    description = [
        "Start or reuse a project daemon, exchange a ping request over a loopback socket, and print the structured " +
            "response. The project is --project-root, or else the nearest directory at or above the working " +
            "directory that contains a .mps directory.",
    ],
)
class DaemonPingCommand(private val environment: CommandEnvironment) : CliCommand() {
    override fun run() {
        val response = environment.daemon().ping()
        println(renderJson(response))
    }
}
