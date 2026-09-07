package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(name = "solution", description = ["Create a solution project module."])
class CreateSolutionCommand(environment: CommandEnvironment) : CreateModuleCommand(environment) {
    @Parameters(index = "0", paramLabel = "MODULE_NAME") lateinit var moduleName: String
    @Option(names = ["--descriptor"], paramLabel = "FILE") var descriptor: String? = null
    @Option(names = ["--usage-preset"], defaultValue = "not-generated") lateinit var usagePreset: String
    override fun run() = render(environment.daemon().createSolution(moduleName, descriptor,
        SolutionUsagePreset.valueOf(usagePreset.replace('-', '_').uppercase()), dryRun))
}
