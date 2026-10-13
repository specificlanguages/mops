package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.Command
import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.TypeConversionException

@Command(name = "solution", description = ["Create a solution project module."])
class CreateSolutionCommand(environment: CommandEnvironment) : CreateModuleCommand(environment) {
    constructor(client: DaemonClient) : this(DaemonClientCommandEnvironment(client))
    @Parameters(index = "0", paramLabel = "MODULE_NAME") lateinit var moduleName: String
    @Option(names = ["--descriptor"], paramLabel = "FILE") var descriptor: String? = null
    @Option(
        names = ["--usage-preset"], paramLabel = "PRESET", defaultValue = "not-generated", converter = [UsagePresetConverter::class],
        description = [
            "Generation-target facets for the solution's intended use: not-generated (no facets, models are only analyzed), " +
                "text (generates plain text), java (generates Java compiled and loaded by MPS), " +
                "java-tests (java plus the tests facet), java-mps-plugin (java whose classes extend MPS as a plugin). " +
                "Default: \${DEFAULT-VALUE}.",
        ],
    )
    lateinit var usagePreset: SolutionUsagePreset
    override fun run() = render(environment.daemon().createSolution(moduleName, descriptor, usagePreset, dryRun))

    class UsagePresetConverter : ITypeConverter<SolutionUsagePreset> {
        override fun convert(value: String): SolutionUsagePreset = SolutionUsagePreset.fromWireName(value)
            ?: throw TypeConversionException("expected one of ${SolutionUsagePreset.entries.joinToString(", ") { it.wireName }}")
    }
}
