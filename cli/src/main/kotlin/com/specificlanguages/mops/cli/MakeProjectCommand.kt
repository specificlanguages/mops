package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.IExitCodeGenerator
import picocli.CommandLine.Option

/**
 * Runs the MPS make on every generatable module in the project. Exits non-zero when the make reports errors.
 */
@Command(
    name = "project",
    description = ["Make every generatable module in the project."],
)
class MakeProjectCommand(private val environment: CommandEnvironment) : CliCommand(), IExitCodeGenerator {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(names = ["--json"], description = ["Print the make result as JSON."])
    var json: Boolean = false

    private var exitCode: Int = 0

    override fun run() {
        val client = environment.daemon()
        exitCode = renderMakeResult(client.makeProject(), json)
    }

    override fun getExitCode(): Int = exitCode
}
