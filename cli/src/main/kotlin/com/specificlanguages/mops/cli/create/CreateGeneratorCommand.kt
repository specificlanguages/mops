package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = "generator", description = ["Create a generator for a language."])
class CreateGeneratorCommand(environment: CommandEnvironment) : CreateModuleCommand(environment) {
    @Option(names = ["--language"], required = true) lateinit var language: String
    @Option(names = ["--alias"], required = true) lateinit var alias: String
    @Option(names = ["--standalone"]) var standalone = false
    @Option(names = ["--descriptor"], paramLabel = "FILE") var descriptor: String? = null
    override fun run() {
        require(standalone || descriptor == null) { "--descriptor requires --standalone" }
        render(environment.daemon().createGenerator(language, alias, standalone, descriptor, dryRun))
    }
}
