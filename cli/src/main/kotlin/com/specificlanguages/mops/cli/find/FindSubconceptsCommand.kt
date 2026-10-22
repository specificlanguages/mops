package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "subconcepts",
    description = [
        "Find extending concepts and interface implementors in all loaded language runtimes, including libraries. " +
            "Excludes the queried concept; prints qualified names in sorted order. Unloaded languages contribute no results.",
    ],
)
class FindSubconceptsCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Parameters(index = "0", paramLabel = "CONCEPT", description = ["Concept name to resolve."])
    lateinit var concept: String

    @Option(names = ["--concrete"], description = ["Exclude abstract concepts and interfaces."])
    var concreteOnly: Boolean = false

    @Option(names = ["--direct"], description = ["Keep only immediate superclass or interface relationships."])
    var direct: Boolean = false

    @Option(names = ["--json"], description = ["Print the qualified concept names as JSON."])
    var json: Boolean = false

    override fun run() {
        val response = environment.daemon().findSubconcepts(concept, concreteOnly, direct)
        if (json) println(renderJson(response)) else response.concepts.forEach(::println)
    }
}
