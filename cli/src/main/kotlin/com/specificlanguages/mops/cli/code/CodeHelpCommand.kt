package com.specificlanguages.mops.cli.code

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(name = "help", description = ["Show the Code Mode extension reference from the selected project daemon."])
class CodeHelpCommand(private val environment: CommandEnvironment) : CliCommand() {
    @Parameters(index = "0", arity = "0..1", paramLabel = "PATH")
    var path: String? = null

    @Option(names = ["--json"], description = ["Emit the catalog as JSON."])
    var json: Boolean = false

    override fun run() {
        print(environment.daemon().codeCatalog(path, json).output)
    }
}
