package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.output.displayConcept
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeTarget
import picocli.CommandLine.Command
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
class FindUsagesCommand(environment: CommandEnvironment) : SearchCommand(environment) {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Parameters(
        arity = "1..*",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET NODE_ID [in SCOPE_SEGMENT...]",
        description = ["Serialized node reference, or model target followed by node ID, then an optional `in` clause."],
    )
    lateinit var arguments: Array<String>

    override fun search(): SearchResults {
        val (targetTokens, scope) = splitUsagesArguments(arguments.toList())
        val client = environment.daemon()
        val response = client.findUsages(target = nodeTarget(targetTokens), limit = limit, scope = scope)
        val target = response.target
        return SearchResults(
            response = response,
            subject = "usages of ${target.name ?: "<unnamed>"} (${displayConcept(target.concept, fullConcept)})",
            scope = response.scope,
            matches = response.usages.map {
                SearchMatch(it.owner.reference, renderText(it, fullConcept, environment.nodeReferenceFormat))
            },
            truncated = response.truncated,
        )
    }

    private fun nodeTarget(targetTokens: List<String>): NodeTarget =
        when (targetTokens.size) {
            1 -> NodeTarget.NodeReference(targetTokens[0])
            2 -> NodeTarget.InModel(modelTarget = targetTokens[0], nodeId = targetTokens[1])
            else -> error("expected one node reference or model target plus node ID, optionally followed by `in <scope>`")
        }

}
