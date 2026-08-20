package com.specificlanguages.mops.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(name = "help", description = ["Show Code Services from the selected project daemon."])
class CodeHelpCommand(private val environment: CommandEnvironment) : CliCommand() {
    @Parameters(index = "0", arity = "0..1", paramLabel = "PATH")
    var path: String? = null

    @Option(names = ["--json"], description = ["Emit the catalog as JSON."])
    var json: Boolean = false

    override fun run() {
        print(environment.daemon().codeCatalog(path, json).output)
    }
}
