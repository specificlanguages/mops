package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(name = "create", description = ["Create a Project Module."])
class CreateOperations : CommandGroup()

abstract class CreateModuleCommand(protected val environment: CommandEnvironment) : CliCommand() {
    @Option(names = ["--dry-run"], description = ["Validate and describe creation without changing the project."])
    var dryRun = false
    @Option(names = ["--json"], description = ["Print structured JSON."])
    var json = false
    protected fun render(response: ModuleCreationResponse) {
        val plan = response.plan; val report = response.report
        when {
            json && plan != null -> println(ProtocolJson.encodeModuleCreationPlan(plan))
            json && report != null -> println(ProtocolJson.encodeModuleCreationReport(report))
            plan != null -> renderPlan(plan)
            report != null -> renderReport(report)
            else -> error("daemon returned neither a creation plan nor a report")
        }
    }
    private fun renderPlan(plan: ModuleCreationPlan) {
        fun entry(value: ModuleCreationPlanEntry) {
            println("would create ${value.kind.name.lowercase()} ${value.moduleName} at ${value.descriptorPath}")
            value.sourceLanguageReference?.let { println("  source language: $it") }
            value.alias?.let { println("  alias: $it") }
            value.persistence?.let { println("  persistence: ${it.name.lowercase()}") }
            value.generatorDirectory?.let { println("  generator directory: $it") }
            value.facets.forEach { println("  facet ${it.type}: ${it.values}") }
        }
        entry(plan.primary); plan.companions.forEach(::entry)
    }
    private fun renderReport(report: ModuleCreationReport) {
        fun entry(value: ModuleCreationEntry, companion: Boolean) {
            println("created ${if (companion) "companion " else ""}${value.kind.name.lowercase()} ${value.moduleName} [${value.moduleReference}] at ${value.descriptorPath}")
        }
        entry(report.primary, false); report.companions.forEach { entry(it, true) }
    }
}

@Command(name = "language", description = ["Create a language Project Module."])
class CreateLanguageCommand(environment: CommandEnvironment) : CreateModuleCommand(environment) {
    constructor(client: DaemonClient) : this(DaemonClientCommandEnvironment(client))
    @Parameters(index = "0", paramLabel = "MODULE_NAME") lateinit var moduleName: String
    @Option(names = ["--descriptor"], paramLabel = "FILE") var descriptor: String? = null
    @Option(names = ["--with-generator"]) var withGenerator = false
    override fun run() = render(environment.daemon().createLanguage(moduleName, descriptor, withGenerator, dryRun))
}
@Command(name = "solution", description = ["Create a solution Project Module."])
class CreateSolutionCommand(environment: CommandEnvironment) : CreateModuleCommand(environment) {
    @Parameters(index = "0", paramLabel = "MODULE_NAME") lateinit var moduleName: String
    @Option(names = ["--descriptor"], paramLabel = "FILE") var descriptor: String? = null
    @Option(names = ["--usage-preset"], defaultValue = "not-generated") lateinit var usagePreset: String
    override fun run() = render(environment.daemon().createSolution(moduleName, descriptor,
        SolutionUsagePreset.valueOf(usagePreset.replace('-', '_').uppercase()), dryRun))
}
@Command(name = "devkit", description = ["Create a devkit Project Module."])
class CreateDevkitCommand(environment: CommandEnvironment) : CreateModuleCommand(environment) {
    @Parameters(index = "0", paramLabel = "MODULE_NAME") lateinit var moduleName: String
    @Option(names = ["--descriptor"], paramLabel = "FILE") var descriptor: String? = null
    override fun run() = render(environment.daemon().createDevkit(moduleName, descriptor, dryRun))
}
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
