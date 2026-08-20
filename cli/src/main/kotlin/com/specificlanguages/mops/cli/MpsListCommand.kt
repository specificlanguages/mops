package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "list",
    aliases = ["ls"],
    description = ["List an MPS navigation target as a bounded tree."],
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
            ListRenderer(fullConcept).renderText(response.root, indent = 0)
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
