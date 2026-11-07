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
    header = ["Find subconcepts of one MPS concept."],
    description = [
        "Find extending concepts and interface implementors in all loaded language runtimes, including libraries. " +
            "By default the results include transitive descendants, abstract concepts, and interfaces. Excludes the " +
            "queried concept; prints qualified names in sorted order. Unloaded languages contribute no results.",
    ],
    footer = [
        "",
        "Text output is one qualified concept name per line; when there are none, it is one " +
            "`# no subconcepts of <concept> in loaded languages` line, naming --concrete and --direct when given. " +
            "--json prints a `concepts` array of qualified names, with the qualified concept as `target` and " +
            "`scope` set to `{\"kind\":\"loaded-languages\"}`. " +
            "Build project languages before querying their hierarchy. CONCEPT resolves the same way as in " +
            "`mops find instances`.",
        "",
        "In Code Mode, mops.search.subConceptsOf(concept, concreteOnly, direct) returns a sorted list of " +
            "SAbstractConcept objects with the same filters; pass the booleans positionally. See " +
            "`mops code help mops.search.subConceptsOf`.",
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
        when {
            json -> println(renderJson(response))
            response.concepts.isEmpty() -> {
                val filters = listOfNotNull("concrete".takeIf { concreteOnly }, "direct".takeIf { direct })
                val subject = (filters + "subconcepts of ${response.target.concept}").joinToString(" ")
                println(emptyResultLine(subject, response.scope, environment.nodeReferenceFormat))
            }
            else -> response.concepts.forEach(::println)
        }
    }
}
