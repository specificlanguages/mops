package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "project",
    description = ["Run MPS's full check over every Project Module and its models."],
)
class ProjectCheckCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(names = ["--format"], paramLabel = "FORMAT", description = ["Output format: human or jsonl."])
    var format: String? = null

    @Option(names = ["--limit"], paramLabel = "N", description = ["Maximum findings to report; 0 means unlimited."])
    var limit: Int = 20

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        renderCheckResponse(environment.daemon().checkProject(limit), format)
    }
}
