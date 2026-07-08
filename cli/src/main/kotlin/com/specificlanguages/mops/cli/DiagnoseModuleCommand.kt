package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(
    name = "module",
    description = ["Diagnose the load state of one module, addressed by name or serialized module reference."],
)
class DiagnoseModuleCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var diagnose: DiagnoseOperations

    @Option(names = ["--json"], description = ["Print the full diagnosis as JSON."])
    var json: Boolean = false

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "MODULE",
        description = ["Module name or serialized module reference."],
    )
    lateinit var module: String

    override fun run() {
        val client = daemonClient ?: diagnose.root.ensureDaemon()
        val response = client.diagnoseModule(module)

        if (json) {
            println(ProtocolJson.encodeResponse(response))
            return
        }

        val diagnostic = response.module
        println(
            listOf(
                diagnostic.module,
                diagnostic.kind,
                "present=${diagnostic.present}",
                "loaded=${diagnostic.loaded}",
            ).joinToString("\t"),
        )
        diagnostic.problem?.let { renderTree(it, depth = 1) }
    }

    private fun renderTree(problem: ModuleLoadProblemJson, depth: Int) {
        val indent = "\t".repeat(depth)
        val columns = listOfNotNull(problem.module, problem.reason, problem.detail)
        println(indent + columns.joinToString("\t"))
        problem.causes.forEach { renderTree(it, depth + 1) }
    }
}
