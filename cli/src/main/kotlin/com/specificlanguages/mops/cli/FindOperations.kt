package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

@Command(
    name = "find",
    description = ["Search editable MPS project sources."],
    subcommands = [FindUsagesCommand::class],
)
class FindOperations : Runnable {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
