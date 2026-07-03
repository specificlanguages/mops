package com.specificlanguages.mops.cli

import picocli.CommandLine.Option

/**
 * Base class for mops CLI commands. Picocli inherits options declared here into every subcommand, so extending this
 * gives each command a `--help` flag without repeating the declaration.
 */
abstract class CliCommand : Runnable {
    @Option(names = ["-h", "--help"], usageHelp = true, description = ["Show this help message and exit."])
    var helpRequested: Boolean = false
}
