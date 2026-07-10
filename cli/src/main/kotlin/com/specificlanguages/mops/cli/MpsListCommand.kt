package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListSummaryGroupJson
import com.specificlanguages.mops.protocol.MpsListSummaryJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(
    name = "list",
    aliases = ["ls"],
    description = ["List an MPS navigation target as a bounded tree."],
)
class MpsListCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var root: MopsCommand

    @Option(
        names = ["--depth"],
        paramLabel = "N",
        description = ["Maximum descendant depth to include, from 0 to 8. Defaults to 1. Not allowed with --summary."],
    )
    var depth: Int? = null

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = ["Maximum children to show per level. Defaults to 50; 0 means unlimited."],
    )
    var limit: Int = 50

    @Option(
        names = ["--summary"],
        description = ["Print grouped counts of the target's children instead of enumerating them. Not allowed with --depth."],
    )
    var summary: Boolean = false

    @Option(
        names = ["--role"],
        paramLabel = "ROLE",
        description = ["Show only the target node's children in this containment role. Valid only for a node target."],
    )
    var role: String? = null

    @Option(
        names = ["--json"],
        description = ["Print the semantic list tree as JSON."],
    )
    var json: Boolean = false

    @Option(
        names = ["--full-concept"],
        description = ["Show fully qualified concept names in text output instead of short names."],
    )
    var fullConcept: Boolean = false

    @Parameters(
        arity = "0..*",
        paramLabel = "TARGET_SEGMENT",
        description = ["MPS navigation target segments. Omit for project root; use / for repository root."],
    )
    var target: List<String> = emptyList()

    override fun run() {
        require(depth == null || depth!! in 0..8) { "depth must be between 0 and 8" }
        require(limit >= 0) { "limit must not be negative" }
        require(!(summary && depth != null)) { "--summary cannot be combined with --depth" }
        require(target.none { it.isEmpty() }) { "target segment must not be blank" }
        require(usesSpaceSeparatedTargetSegments()) {
            "target segments must be space-separated; use / only for repository root or pass a serialized node reference as one target"
        }

        val requestedTarget = target.takeIf { it.isNotEmpty() }

        val client = daemonClient ?: root.ensureDaemon()
        val response = client.list(
            target = requestedTarget,
            depth = depth ?: DEFAULT_DEPTH,
            limit = limit,
            summary = summary,
            role = role,
        )
        if (json) {
            println(ProtocolJson.encodeListEntry(response.root))
        } else {
            renderText(response.root, indent = 0)
        }
    }

    private fun renderText(entry: MpsListEntryJson, indent: Int) {
        println("${"  ".repeat(indent)}${entry.columns().joinToString("\t")}")

        val summary = entry.summary
        if (summary != null) {
            summary.groups.forEach { group ->
                println("${"  ".repeat(indent + 1)}${summaryColumns(summary, group).joinToString("\t")}")
            }
            return
        }

        entry.children.orEmpty().forEach { child -> renderText(child, indent + 1) }
        entry.childTotal?.let { total ->
            val shown = entry.children.orEmpty().size
            println("${"  ".repeat(indent + 1)}${listOf("truncated", shown, total).joinToString("\t")}")
        }
    }

    private fun summaryColumns(
        summary: MpsListSummaryJson,
        group: MpsListSummaryGroupJson,
    ): List<String> {
        // A "concept" grouping keys each group by a concept qualified name; the other axes key by role, model, or kind.
        val key = if (summary.by == "concept") displayConcept(group.key, fullConcept) else group.key
        val concepts = group.concepts?.takeIf { it.isNotEmpty() }
            ?.let { listOf(it.joinToString(", ") { concept -> displayConcept(concept, fullConcept) }) }
            ?: emptyList()
        return listOf(summary.by, key, group.count.toString()) + concepts
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
        listOf(name ?: "<unnamed>", concept?.let { displayConcept(it, fullConcept) }.orEmpty(), reference.orEmpty()) +
            listOfNotNull(error)

    private fun usesSpaceSeparatedTargetSegments(): Boolean =
        target.none { it.contains("/") } ||
            target == listOf("/") ||
            target.size == 1 && target.single().startsWith("r:") && target.single().contains("/")

    private companion object {
        const val DEFAULT_DEPTH = 1
    }
}
