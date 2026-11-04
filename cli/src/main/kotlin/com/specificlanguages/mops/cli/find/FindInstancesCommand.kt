package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeFilter
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "instances",
    header = ["Find instances of one MPS concept."],
    description = [
        "Find instances of one MPS concept, optionally narrowed by `--named` and `--role` (which AND together with " +
            "each other and the scope). Searches editable project sources by default; append `in <scope-segments>` to " +
            "search a module, model, or subtree exhaustively, or `in /` for the whole repository. See " +
            "`mops explain scope`.",
    ],
    footer = [
        "",
        "A CONCEPT that does not resolve fails with CONCEPT_NOT_FOUND and names the cause: the name is not well " +
            "formed; the owning language is unknown to the project; the language is present but not loaded (with its " +
            "load diagnosis and a pointer at `mops diagnose module`, since only loaded languages contribute concepts " +
            "to name lookup); or the loaded language has no such concept (with similarly named concepts from it).",
        "",
        "A bare short name that names concepts in several languages fails with AMBIGUOUS_TARGET and lists the " +
            "qualified candidates. While a project language is unbuilt, a bare short name fails with " +
            "LANGUAGE_NOT_LOADED instead of guessing, since the unbuilt language could define it too. An existing " +
            "concept with no matches succeeds with no rows.",
        "",
        "Text output is one tab-separated row per node: `root` or `node`, the node name (or <unnamed>), the node's " +
            "concept, and its serialized reference. A non-root node appends `parent` and the parent's name (or " +
            "<unnamed>), concept, and reference. --json prints an object with `limit`, `truncated`, and a `nodes` " +
            "array whose non-root entries carry a nested `parent` summary. See `mops explain output`.",
    ],
)
class FindInstancesCommand(environment: CommandEnvironment) : NodeSearchCommand(environment) {
    constructor(
        daemonClient: DaemonClient,
        nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
    ) : this(DaemonClientCommandEnvironment(daemonClient, nodeReferenceFormat))

    @Option(
        names = ["--exact"],
        description = ["Match only nodes whose direct concept is the queried concept."],
    )
    var exact: Boolean = false

    @Option(
        names = ["--named"],
        paramLabel = "PATTERN",
        description = [
            "Keep only instances whose node name matches this Go-to-Node pattern; supports camel-hump and '*' " +
                "wildcards. See `mops explain name-pattern`.",
        ],
    )
    var named: String? = null

    @Option(
        names = ["--role"],
        paramLabel = "ROLE",
        description = [
            "Keep only instances filling this containment role in their parent.",
        ],
    )
    var role: String? = null

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "CONCEPT",
        description = [
            "The MPS concept to search for: its qualified name (<language>.structure.<ConceptName>), the same name with " +
                ".structure. dropped, or a bare short name that is unique across loaded languages.",
        ],
    )
    lateinit var concept: String

    override fun run() {
        val scope = validatedScope()
        val filters = buildList {
            named?.let { add(NodeFilter.Named(it)) }
            role?.let { add(NodeFilter.Role(it)) }
        }
        val client = environment.daemon()
        val response = client.findInstances(
            concept = concept,
            exact = exact,
            scope = scope,
            filters = filters,
            limit = limit,
        )
        printResults(response, response.nodes, response.truncated)
    }
}
