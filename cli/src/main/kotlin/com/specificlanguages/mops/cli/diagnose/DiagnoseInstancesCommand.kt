package com.specificlanguages.mops.cli.diagnose

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.find.scopeClauseSegments
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeFilter
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "instances",
    description = ["Diagnose missing concept instances by comparing MPS search with tree traversal. " +
        "Loads and scans every model in scope; defaults to editable project sources. Does not repair caches."],
)
class DiagnoseInstancesCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(client: DaemonClient) : this(DaemonClientCommandEnvironment(client))

    @Option(names = ["--json"], description = ["Print the full diagnosis as JSON."])
    var json = false

    @Option(names = ["--exact"], description = ["Match the direct concept only."])
    var exact = false

    @Option(names = ["--named"], paramLabel = "PATTERN", description = ["Explain the find name filter."])
    var named: String? = null

    @Option(names = ["--role"], paramLabel = "ROLE", description = ["Explain the containment-role filter."])
    var role: String? = null

    @Option(names = ["--limit"], paramLabel = "N", description = ["Simulate find's result limit (default 100; 0 unlimited). Does not limit diagnosis."])
    var limit = 100

    @Option(names = ["--expect"], paramLabel = "NODE_REF", description = ["Explain whether a serialized node reference is returned."])
    var expect: String? = null

    @Parameters(index = "0", arity = "1", paramLabel = "CONCEPT")
    lateinit var concept: String

    @Parameters(index = "1..*", paramLabel = "[in SCOPE_SEGMENT...]")
    var scopeClause: List<String> = emptyList()

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        val scope = scopeClauseSegments(scopeClause)
        val filters = buildList {
            named?.let { add(NodeFilter.Named(it)) }
            role?.let { add(NodeFilter.Role(it)) }
        }
        val result = environment.daemon().diagnoseInstances(concept, exact, scope, filters, limit, expect)
        if (json) {
            println(renderJson(result))
            return
        }
        println("concept\t${result.concept.name}\t${result.concept.id}\tvalid=${result.concept.valid}")
        println("search\t${result.searchPath}\tcomplete=${result.complete}\tmps=${result.mpsVersion}\texact=${result.exact}")
        println("scope\t${result.scope?.joinToString(" ") ?: "editable project sources"}")
        println("results\tnormal=${result.normalCount}\tfiltered=${result.filteredCount}\treturned=${result.returnedCount}\tlimit=${result.limit}")
        result.filters.forEach { println("filter\t$it") }
        result.participants.forEach { println("participant\t$it") }
        result.models.forEach { model ->
            println("model\t${model.model}\tfacade=${model.facadeCount}\tlookup=${model.lookupCount}\tquery-tree=${model.queryTreeCount}\tsemantic-tree=${model.semanticTreeCount}")
            println("\tstate\t${model.implementation}\treadOnly=${model.readOnly}\tchanged=${model.changed}\tloadedBefore=${model.loadedBefore}")
            println("\tsource\t${model.sourceType}\t${model.source}")
            model.streams.forEach { println("\tstream\t$it") }
            model.differences.forEach { difference ->
                println("\tdifference\t${difference.layer}\tmissing=${difference.missingCount}\tunexpected=${difference.unexpectedCount}")
                difference.missing.forEach { println("\t\tmissing\t$it") }
                difference.unexpected.forEach { println("\t\tunexpected\t$it") }
            }
            model.errors.forEach { println("\tincomplete\t$it") }
        }
        result.expected?.let { node ->
            println("expected\t${node.reference}\tstatus=${node.status}\tresolved=${node.resolved}\tinScope=${node.inScope}\tsemanticMatch=${node.semanticMatch}\tinExpandedConcepts=${node.inExpandedConcepts}")
            node.concept?.let { println("\tconcept\t${it.name}\t${it.id}\tvalid=${it.valid}") }
            println("\tsearch\tfacade=${node.inFacade}\tlookup=${node.inLookup}\tnormal=${node.inNormalSearch}\treturned=${node.returned}")
            node.rejectedFilters.forEach { println("\trejected-filter\t$it") }
        }
        result.errors.forEach { println("incomplete\t$it") }
    }
}
