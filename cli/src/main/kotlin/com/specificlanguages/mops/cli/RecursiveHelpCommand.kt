package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Help
import picocli.CommandLine.IHelpCommandInitializable2
import picocli.CommandLine.Option
import picocli.CommandLine.ParameterException
import picocli.CommandLine.Parameters
import java.io.PrintWriter

/**
 * Help subcommand that resolves the full path of `COMMAND` segments through the subcommand hierarchy and prints the
 * usage of the deepest named command. Unlike picocli's stock `HelpCommand`, which only resolves the first segment, this
 * walks every segment so `mops help <group> <command>` prints the leaf command's own usage.
 */
@Command(
    name = "help",
    header = ["Display help information about the specified command."],
    synopsisHeading = "%nUsage: ",
    helpCommand = true,
    description = [
        "%nWhen no COMMAND is given, the usage help for the main command is displayed.",
        "If a COMMAND is specified, the help for that command is shown.%n",
    ],
)
class RecursiveHelpCommand : IHelpCommandInitializable2, Runnable {
    @Option(names = ["-h", "--help"], usageHelp = true, description = ["Show usage help for the help command and exit."])
    var helpRequested: Boolean = false

    @Parameters(paramLabel = "COMMAND", arity = "0..*", description = ["The COMMAND to display the usage help message for."])
    var commands: Array<String> = emptyArray()

    private var self: CommandLine? = null
    private lateinit var outWriter: PrintWriter
    private lateinit var colorScheme: Help.ColorScheme

    override fun run() {
        var current = self?.parent ?: return

        for (segment in commands) {
            val subcommand = current.subcommands[segment]
                ?: throw ParameterException(current, "Unknown subcommand '$segment'.", null, segment)
            current = subcommand
        }

        current.usage(outWriter, colorScheme)
    }

    override fun init(
        helpCommandLine: CommandLine,
        colorScheme: Help.ColorScheme,
        outWriter: PrintWriter,
        errWriter: PrintWriter,
    ) {
        this.self = helpCommandLine
        this.colorScheme = colorScheme
        this.outWriter = outWriter
    }
}
