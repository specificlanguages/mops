package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

/**
 * Picocli command group for model edit operations.
 */
@Command(
    name = "edit",
    mixinStandardHelpOptions = true,
    description = ["Apply edit operations through the mops daemon."],
    subcommands = [
        EditApplyCommand::class,
    ],
)
class EditOperations : Runnable {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
