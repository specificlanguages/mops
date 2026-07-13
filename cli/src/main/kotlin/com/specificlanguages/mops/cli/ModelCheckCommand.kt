package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

@Command(
    name = "check",
    description = ["Run MPS's full Model Check over one model and report the findings."],
)
class ModelCheckCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var model: ModelOperations

    @Option(
        names = ["--format"],
        paramLabel = "FORMAT",
        description = [
            "How to print findings: human (a readable list sorted by severity; default) or jsonl (one finding " +
                "object per line).",
        ],
    )
    var format: String? = null

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = [
            "Maximum findings to report, most severe first. Defaults to 20 so a broken model does not flood the " +
                "output; 0 means unlimited.",
        ],
    )
    var limit: Int = 20

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "MODEL_TARGET",
        description = ["Model name or serialized model reference."],
    )
    lateinit var target: String

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        val outputFormat = resolveFormat()

        val client = daemonClient ?: model.root.ensureDaemon()
        val response = client.checkModel(target, limit)

        when (outputFormat) {
            OutputFormat.HUMAN -> {
                response.findings.forEach(::renderHuman)
                println(summaryLine(response))
            }

            OutputFormat.JSONL -> response.findings.forEach { println(ProtocolJson.encodeFinding(it)) }
        }
    }

    private fun renderHuman(finding: ModelCheckFindingJson) {
        val columns = mutableListOf(finding.severity.name.lowercase(), finding.message)
        finding.node?.let { columns += listOf(it.name ?: "<unnamed>", it.concept, it.reference) }
        println(columns.joinToString("\t"))
    }

    // Closes human output with total counts by severity over the full (pre-truncation) finding set, so zero errors is
    // legible without re-running unbounded. When findings were dropped the truncation notice folds in as "(showing N)".
    private fun summaryLine(response: ModelCheckResponse): String {
        val totals = response.totals
        if (totals.total == 0) return "no findings"
        val counts = listOf(
            count(totals.errors, "error"),
            count(totals.warnings, "warning"),
            count(totals.infos, "info", "infos"),
        ).joinToString(", ")
        return if (response.truncated) "$counts (showing ${response.findings.size})" else counts
    }

    private fun count(n: Int, singular: String, plural: String = "${singular}s"): String =
        "$n ${if (n == 1) singular else plural}"

    private fun resolveFormat(): OutputFormat {
        val requested = format ?: return OutputFormat.HUMAN
        return OutputFormat.fromWireName(requested)
            ?: throw IllegalArgumentException("unknown --format value '$requested'; expected human or jsonl")
    }

    private enum class OutputFormat {
        HUMAN,
        JSONL,
        ;

        companion object {
            fun fromWireName(value: String): OutputFormat? =
                when (value) {
                    "human" -> HUMAN
                    "jsonl" -> JSONL
                    else -> null
                }
        }
    }
}
