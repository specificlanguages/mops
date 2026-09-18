package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "module",
    description = ["Run MPS's full check over one or more Project Modules and their models."],
)
class ModuleCheckCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(names = ["--format"], paramLabel = "FORMAT", description = ["Output format: human or jsonl."])
    var format: String? = null

    @Option(names = ["--limit"], paramLabel = "N", description = ["Maximum findings to report; 0 means unlimited."])
    var limit: Int = 20

    @Parameters(arity = "1..*", paramLabel = "MODULE", description = ["Project Module name or serialized reference."])
    lateinit var modules: List<String>

    override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        renderCheckResponse(environment.daemon().checkModules(modules, limit), format)
    }
}
