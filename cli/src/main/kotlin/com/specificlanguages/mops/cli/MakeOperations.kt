package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

/**
 * Picocli command group for running the MPS make (generation and compilation) on the project.
 */
@Command(
    name = "make",
    description = ["Run the MPS make (generation and compilation) on modules or the whole project."],
    subcommands = [
        MakeModulesCommand::class,
        MakeProjectCommand::class,
        RecursiveHelpCommand::class,
    ],
)
class MakeOperations : CliCommand() {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
