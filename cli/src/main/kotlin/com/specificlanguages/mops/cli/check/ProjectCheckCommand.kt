package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelCheckResponse
import picocli.CommandLine.Command

@Command(
    name = "project",
    header = ["Run MPS's full check over every project module and its models."],
    description = [
        "Run MPS's full check over every project module and its models.",
    ],
)
class ProjectCheckCommand(environment: CommandEnvironment) : CheckCommand(environment) {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    override fun check(client: DaemonClient): ModelCheckResponse = client.checkProject(limit)

    override fun noRootsError(): String = "the project has no roots"
}
