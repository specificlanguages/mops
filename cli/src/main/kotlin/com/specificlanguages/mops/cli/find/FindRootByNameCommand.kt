package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = "root-by-name",
    header = ["Find root nodes by name pattern."],
    description = [
        "Find root nodes by name using MPS Go-to-Node pattern matching. Searches editable project sources by " +
            "default; append `in <scope-segments>` to search a module, model, or the whole repository (`in /`). Only " +
            "root-bearing scopes are valid. See `mops explain name-pattern` and `mops explain scope`.",
    ],
    footer = [
        "",
        "Text output has the same rows as `mops find instances`; --json prints the structured response, " +
            "with the `pattern` as `target`.",
    ],
)
class FindRootByNameCommand(environment: CommandEnvironment) : NodeSearchCommand(environment) {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "PATTERN",
        description = ["Name pattern; supports camel-hump and '*' wildcards. See `mops explain name-pattern`."],
    )
    lateinit var pattern: String

    override fun run() {
        require(pattern.isNotBlank()) { "pattern must not be blank" }
        val scope = validatedScope()
        val client = environment.daemon()
        val response = client.findByName(pattern = pattern, limit = limit, scope = scope)
        printResults(
            response,
            response.nodes,
            response.truncated,
            "root nodes matching ${response.target.pattern}",
            response.scope,
        )
    }
}
