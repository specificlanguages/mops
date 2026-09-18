package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "model",
    description = ["Run MPS's full model check over one model and report the findings."],
)
class ModelCheckCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

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
        val client = environment.daemon()
        val response = client.checkModel(target, limit)
        renderCheckResponse(response, format)
    }
}
