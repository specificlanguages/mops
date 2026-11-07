package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.output.displayConcept
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.cli.output.truncationLine
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.NodeTarget
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "usages",
    header = ["Find references to one MPS node."],
    description = [
        "Find references to one MPS node. Searches editable project sources by default; append " +
            "`in <scope-segments>` to search a module, model, or subtree exhaustively, or `in /` for the whole " +
            "repository. See `mops explain scope`.",
    ],
    footer = [
        "",
        "The target is addressed the same way as in `mops get node`; an unresolved target fails with " +
            "NODE_NOT_FOUND.",
        "",
        "Text output is one tab-separated row per reference: `usage`, the reference role, and the owning node's " +
            "name (or <unnamed>), concept, and reference. The owner is typed `root` or `node` by its position in its " +
            "model; a non-root owner appends `parent` and the parent's name (or <unnamed>), concept, and reference. " +
            "--json prints an object with `target` (the resolved node's summary), `scope`, `limit`, `truncated`, and " +
            "a `usages` array. Each entry carries the " +
            "reference `role` and a nested `owner` node summary; a non-root owner carries a nested `parent` " +
            "summary. --refs-only prints one reference per referencing node. See `mops explain output`.",
    ],
)
class FindUsagesCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(
        names = ["--json"],
        description = ["Print usage results as JSON."],
    )
    var json: Boolean = false

    @Option(
        names = ["--full-concept"],
        description = ["Show fully qualified concept names in text output instead of short names."],
    )
    var fullConcept: Boolean = false

    @Option(
        names = ["--refs-only"],
        description = [
            "Print one serialized node reference per line and nothing else, for piping. Cannot combine with --json; " +
                "truncation and an empty result are reported on stderr.",
        ],
    )
    var refsOnly: Boolean = false

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = [
            "Maximum usages to return. Defaults to 100; 0 means unlimited; negative values are rejected. Only when " +
                "more matches exist, text output ends with a `# truncated` line and JSON sets `truncated`.",
        ],
    )
    var limit: Int = 100

    @Parameters(
        arity = "1..*",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET NODE_ID [in SCOPE_SEGMENT...]",
        description = ["Serialized node reference, or model target followed by node ID, then an optional `in` clause."],
    )
    lateinit var arguments: Array<String>

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        require(!(refsOnly && json)) { "--refs-only cannot be combined with --json" }
        val (targetTokens, scope) = splitUsagesArguments(arguments.toList())
        val client = environment.daemon()
        val response = client.findUsages(target = nodeTarget(targetTokens), limit = limit, scope = scope)
        if (!json && response.usages.isEmpty()) {
            val target = response.target
            val subject = "usages of ${target.name ?: "<unnamed>"} (${displayConcept(target.concept, fullConcept)})"
            printMetadata(emptyResultLine(subject, response.scope, environment.nodeReferenceFormat), refsOnly)
            return
        }
        when {
            json -> println(renderJson(response))
            refsOnly -> {
                response.usages.forEach {
                    println(renderNodeReference(it.owner.reference, environment.nodeReferenceFormat, hyperlinks = false))
                }
                if (response.truncated) reportTruncationOnStderr(response.usages.size)
            }
            else -> {
                response.usages.forEach { println(renderText(it, fullConcept, environment.nodeReferenceFormat)) }
                if (response.truncated) println(truncationLine(shown = response.usages.size))
            }
        }
    }

    private fun nodeTarget(targetTokens: List<String>): NodeTarget =
        when (targetTokens.size) {
            1 -> NodeTarget.NodeReference(targetTokens[0])
            2 -> NodeTarget.InModel(modelTarget = targetTokens[0], nodeId = targetTokens[1])
            else -> error("expected one node reference or model target plus node ID, optionally followed by `in <scope>`")
        }

}
