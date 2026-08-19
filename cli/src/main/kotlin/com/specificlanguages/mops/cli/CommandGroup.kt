package com.specificlanguages.mops.cli

import picocli.CommandLine
import picocli.CommandLine.Spec

/** A command namespace whose direct invocation prints its own help. */
abstract class CommandGroup : CliCommand() {
    @Spec
    lateinit var commandSpec: CommandLine.Model.CommandSpec

    override fun run() {
        commandSpec.commandLine().usage(System.out)
    }
}
