package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(name = "model", description = ["Create an empty model in a project module."])
class CreateModelCommand(private val environment: CommandEnvironment) : CliCommand() {
    @Parameters(index = "0", paramLabel = "MODEL_NAME") lateinit var modelName: String
    @Option(names = ["--module"], required = true, paramLabel = "MODULE") lateinit var module: String
    @Option(names = ["--file-per-root"], description = ["Use file-per-root XML persistence."]) var filePerRoot = false
    @Option(names = ["--dry-run"], description = ["Validate and describe creation without changing the project."]) var dryRun = false
    @Option(names = ["--json"], description = ["Print structured JSON."]) var json = false

    override fun run() {
        val response = environment.daemon().createModel(modelName, module, filePerRoot, dryRun)
        val plan = response.plan
        val report = response.report
        when {
            json && plan != null -> println(ProtocolJson.encodeModelCreationPlan(plan))
            json && report != null -> println(ProtocolJson.encodeModelCreationReport(report))
            plan != null -> println("would create model ${plan.modelName} in module ${plan.moduleName} [${plan.moduleReference}]\n" +
                "  persistence: ${plan.persistence.render()}\n  location: ${plan.location}")
            report != null -> println("created model ${report.modelName} [${report.modelReference}] in module " +
                "${report.moduleName} [${report.moduleReference}]\n  persistence: ${report.persistence.render()}\n  location: ${report.location}")
            else -> error("daemon returned neither a model creation plan nor a report")
        }
    }

    private fun ModelPersistence.render() = name.lowercase().replace('_', '-')
}
