package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.Option

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
