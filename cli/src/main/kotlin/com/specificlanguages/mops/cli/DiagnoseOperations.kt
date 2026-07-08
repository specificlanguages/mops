package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

/**
 * Picocli command group for diagnosing the daemon's view of an MPS project.
 */
@Command(
    name = "diagnose",
    description = ["Diagnose how the daemon loaded the MPS project."],
    subcommands = [
        DiagnoseModulesCommand::class,
        DiagnoseModuleCommand::class,
        RecursiveHelpCommand::class,
    ],
)
class DiagnoseOperations : CliCommand() {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
