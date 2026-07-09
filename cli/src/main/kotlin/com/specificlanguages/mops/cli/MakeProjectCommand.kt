package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.IExitCodeGenerator
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand

/**
 * Runs the MPS make on every generatable module in the project. Exits non-zero when the make reports errors.
 */
@Command(
    name = "project",
    description = ["Make every generatable module in the project."],
)
class MakeProjectCommand(private val daemonClient: DaemonClient? = null) : CliCommand(), IExitCodeGenerator {
    @ParentCommand
    lateinit var make: MakeOperations

    @Option(names = ["--json"], description = ["Print the make result as JSON."])
    var json: Boolean = false

    private var exitCode: Int = 0

    override fun run() {
        val client = daemonClient ?: make.root.ensureDaemon()
        exitCode = renderMakeResult(client.makeProject(), json)
    }

    override fun getExitCode(): Int = exitCode
}
