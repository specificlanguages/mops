package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelCheckResponse
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = "module",
    description = [
        "Run MPS's full check over one or more Project Modules and their models. Exits 1 when any finding is an error.",
    ],
)
class ModuleCheckCommand(environment: CommandEnvironment) : CheckCommand(environment) {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Parameters(arity = "1..*", paramLabel = "MODULE", description = ["Project Module name or serialized reference."])
    lateinit var modules: List<String>

    override fun check(client: DaemonClient): ModelCheckResponse = client.checkModules(modules, limit)
}
