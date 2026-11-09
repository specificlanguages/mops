package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = "node-by-id",
    header = ["Find every node with a given node ID."],
    description = [
        "Find every node with a given node ID. A node ID is unique only within its model, so this reports one match " +
            "per model that holds it, each with its full node reference — never guessing one over another. The ID " +
            "accepts either spelling: the decimal form mops prints, or the encoded form persisted in .mps files. " +
            "Searches editable project sources by default; append `in <scope-segments>` to search a module, model, " +
            "node subtree, or the whole repository (`in /`). See `mops explain node-ref` and `mops explain scope`.",
    ],
)
class FindNodeByIdCommand(environment: CommandEnvironment) : NodeSearchCommand(environment) {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "ID",
        description = ["Node ID in either spelling: the decimal form mops prints or the encoded form from .mps files."],
    )
    lateinit var id: String

    override fun checkQuery() {
        require(id.isNotBlank()) { "ID must not be blank" }
    }

    override fun search(scope: List<String>?): SearchResults {
        val response = environment.daemon().findNodeById(nodeId = id, limit = limit, scope = scope)
        return nodeResults(
            response,
            "nodes with ID ${response.target.nodeId}",
            response.scope,
            response.nodes,
            response.truncated,
        )
    }
}
