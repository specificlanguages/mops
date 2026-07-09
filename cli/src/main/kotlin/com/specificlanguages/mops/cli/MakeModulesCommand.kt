package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.IExitCodeGenerator
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

/**
 * Runs the MPS make on one or more named modules and their transitive dependency closure, so any un-made dependency is
 * made too. Exits non-zero when the make reports errors.
 */
@Command(
    name = "modules",
    aliases = ["module"],
    description = ["Make the named modules and their dependency closure."],
)
class MakeModulesCommand(private val daemonClient: DaemonClient? = null) : CliCommand(), IExitCodeGenerator {
    @ParentCommand
    lateinit var make: MakeOperations

    @Option(names = ["--json"], description = ["Print the make result as JSON."])
    var json: Boolean = false

    @Parameters(
        arity = "1..*",
        paramLabel = "MODULE",
        description = ["Module name or serialized module reference."],
    )
    lateinit var modules: List<String>

    private var exitCode: Int = 0

    override fun run() {
        val client = daemonClient ?: make.root.ensureDaemon()
        exitCode = renderMakeResult(client.makeModules(modules), json)
    }

    override fun getExitCode(): Int = exitCode
}
