package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.MpsListEntryJson
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand
import picocli.CommandLine.Spec

@Command(
    name = "list",
    aliases = ["ls"],
    description = ["List a loaded MPS navigation target as a bounded tree."],
)
class MpsListCommand(private val daemonClient: DaemonClient? = null) : Runnable {
    @ParentCommand
    lateinit var root: MopsCommand

    @Spec
    lateinit var spec: CommandSpec

    @Option(
        names = ["--depth"],
        paramLabel = "N",
        description = ["Maximum descendant depth to include, from 0 to 8. Defaults to 1."],
    )
    var depth: Int = 1

    @Option(
        names = ["--json"],
        description = ["Print the semantic list tree as JSON."],
    )
    var json: Boolean = false

    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "TARGET",
        description = ["MPS navigation target. Omit for project root; use / for loaded repository root."],
    )
    var target: String? = null

    override fun run() {
        require(depth in 0..8) { "depth must be between 0 and 8" }
        val requestedTarget = target
        require(requestedTarget == null || requestedTarget.isNotEmpty()) { "target must not be blank" }

        val client = daemonClient ?: root.ensureDaemon()
        val response = client.list(target = requestedTarget, depth = depth)
        if (json) {
            spec.commandLine().out.println(GsonCodec.toJson(response.root))
        } else {
            renderText(response.root, indent = 0)
        }
    }

    private fun renderText(entry: MpsListEntryJson, indent: Int) {
        spec.commandLine().out.println("${"  ".repeat(indent)}${entry.columns().joinToString("\t")}")
        entry.children.orEmpty().forEach { child -> renderText(child, indent + 1) }
    }

    private fun MpsListEntryJson.columns(): List<String> =
        when (type) {
            "project" -> listOf("project", name.orEmpty())
            "repository" -> listOf("repository", name.orEmpty())
            "module" -> listOf(moduleKind ?: "other", name.orEmpty(), reference.orEmpty())
            "model" -> listOf("model", name.orEmpty(), reference.orEmpty())
            "root" -> nodeColumns("root")
            "node" -> listOf("node", role.orEmpty()) + nodeColumnsWithoutType()
            else -> listOf(type, name.orEmpty(), reference.orEmpty())
        }

    private fun MpsListEntryJson.nodeColumns(typeColumn: String): List<String> =
        listOf(typeColumn) + nodeColumnsWithoutType()

    private fun MpsListEntryJson.nodeColumnsWithoutType(): List<String> =
        listOf(name ?: "<unnamed>", concept.orEmpty(), reference.orEmpty()) + listOfNotNull(error)
}
