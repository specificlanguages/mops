package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModuleLoadDiagnosticJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand

@Command(
    name = "modules",
    description = ["Report which project languages and Java-bearing modules loaded, and why each unloaded one failed."],
)
class DiagnoseModulesCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var diagnose: DiagnoseOperations

    @Option(names = ["--json"], description = ["Print the full diagnosis as JSON."])
    var json: Boolean = false

    @Option(names = ["--all"], description = ["Also list modules that loaded, not just the failed ones."])
    var all: Boolean = false

    override fun run() {
        val client = daemonClient ?: diagnose.root.ensureDaemon()
        val response = client.diagnoseModules()

        if (json) {
            println(ProtocolJson.encodeResponse(response))
            return
        }

        val summary = response.summary
        println(listOf("modules", "${summary.loaded}/${summary.total} loaded", "${summary.failed} failed").joinToString("\t"))

        val rows = if (all) response.modules else response.modules.filterNot { it.loaded }
        rows.forEach(::renderModule)

        println("note\tmodules without a Java facet are not listed; diagnose one with: mops diagnose module <module>")
    }

    private fun renderModule(module: ModuleLoadDiagnosticJson) {
        val problem = module.problem
        if (problem == null) {
            println(listOf(module.module, module.kind, "loaded").joinToString("\t"))
            return
        }
        println(listOf(module.module, module.kind, problem.reason).joinToString("\t"))
        // Root causes are the leaves of the problem tree: the actual modules to fix.
        rootCauses(problem).forEach { println("\t${it.module}\t${it.reason}") }
    }

    // The same root-cause module is often reachable through several dependency paths; collapse those to one row each.
    private fun rootCauses(problem: ModuleLoadProblemJson): List<ModuleLoadProblemJson> =
        if (problem.causes.isEmpty()) emptyList() else problem.causes.flatMap(::leaves).distinctBy { it.module to it.reason }

    private fun leaves(problem: ModuleLoadProblemJson): List<ModuleLoadProblemJson> =
        if (problem.causes.isEmpty()) listOf(problem) else problem.causes.flatMap(::leaves)
}
