package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(name = "devkit", description = ["Create a devkit project module."])
class CreateDevkitCommand(environment: CommandEnvironment) : CreateModuleCommand(environment) {
    @Parameters(index = "0", paramLabel = "MODULE_NAME") lateinit var moduleName: String
    @Option(names = ["--descriptor"], paramLabel = "FILE") var descriptor: String? = null
    override fun run() = render(environment.daemon().createDevkit(moduleName, descriptor, dryRun))
}
