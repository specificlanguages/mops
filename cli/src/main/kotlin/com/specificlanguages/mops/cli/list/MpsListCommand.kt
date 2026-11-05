package com.specificlanguages.mops.cli.list

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "list",
    aliases = ["ls"],
    description = ["List an MPS navigation target as a bounded tree."],
    footer = [
        "",
        "Space-separated target segments address a module, model, or node. After a module, a `.suffix` model segment " +
            "expands against the module name, and a segment that is a single `.` names the model whose name equals " +
            "the module name; that name alone is ambiguous between the module and the model.",
        "",
        "Text output is one indented tab-separated row per entry, tagged by kind (`project`, `repository`, " +
            "`module`, `model`, `root`, or `node`); --json prints the semantic tree. A level wider than --limit ends " +
            "in an indented `# truncated: showing <shown> of <total> children; pass --limit 0 for all` line. See " +
            "`mops explain output`.",
        "",
        "--summary groups the target's children: per role, with the dominant concepts, for a node; per concept " +
            "for a model's roots; per model for a module; per kind for the project or repository.",
    ],
)
class MpsListCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

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

        val client = environment.daemon()
        val response = client.list(
            target = requestedTarget,
            depth = depth ?: DEFAULT_DEPTH,
            limit = limit,
            summary = summary,
            role = role,
        )
        if (json) {
            println(renderJson(response.root))
        } else {
            ListRenderer(fullConcept, environment.nodeReferenceFormat).renderText(response.root, indent = 0)
        }
    }

    private fun usesSpaceSeparatedTargetSegments(): Boolean =
        target.none { it.contains("/") } ||
            target == listOf("/") ||
            target.size == 1 && target.single().startsWith("r:") && target.single().contains("/")

    private companion object {
        const val DEFAULT_DEPTH = 1
    }
}
