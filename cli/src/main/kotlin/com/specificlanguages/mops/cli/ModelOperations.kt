package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand

/**
 * Picocli command group for model-level operations that require an MPS project.
 */
@Command(
    name = "model",
    mixinStandardHelpOptions = true,
    description = ["Run model operations through the mops daemon."],
    subcommands = [
        ModelGetNodeCommand::class,
        ModelResaveCommand::class,
    ],
)
class ModelOperations : Runnable {
    @ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
