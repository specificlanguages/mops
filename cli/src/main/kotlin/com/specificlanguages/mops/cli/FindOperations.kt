package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

@Command(
    name = "find",
    description = ["Search editable MPS project sources."],
    subcommands = [
        FindInstancesCommand::class,
        FindUsagesCommand::class,
        FindByNameCommand::class,
        RecursiveHelpCommand::class,
    ],
)
class FindOperations : CliCommand() {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
