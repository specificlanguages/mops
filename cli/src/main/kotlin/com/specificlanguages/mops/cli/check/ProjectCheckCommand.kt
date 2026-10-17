package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelCheckResponse
import picocli.CommandLine.Command

@Command(
    name = "project",
    description = [
        "Run MPS's full check over every project module and its models. Exits 1 when any finding is an error.",
    ],
)
class ProjectCheckCommand(environment: CommandEnvironment) : CheckCommand(environment) {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    override fun check(client: DaemonClient): ModelCheckResponse = client.checkProject(limit)
}
