package com.specificlanguages.mops.cli

import picocli.CommandLine

/**
 * Picocli command group for daemon lifecycle operations.
 */
@CommandLine.Command(
    name = "daemon",
    mixinStandardHelpOptions = true,
    description = ["Inspect or control mops daemon processes."],
    subcommands = [
        DaemonPingCommand::class,
        DaemonStatusCommand::class,
        DaemonStopCommand::class,
    ],
)
class DaemonOperations : Runnable {
    @CommandLine.ParentCommand
    lateinit var root: MopsCommand

    override fun run() {
        CommandLine(this).usage(System.out)
    }
}
