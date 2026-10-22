package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelCheckResponse
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = "model",
    header = ["Run MPS's full model check over one model."],
    description = [
        "Run MPS's full model check over one model and report the findings. Exits 1 when any finding is an error.",
    ],
)
class ModelCheckCommand(environment: CommandEnvironment) : CheckCommand(environment) {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "MODEL_TARGET",
        description = ["Model name or serialized model reference."],
    )
    lateinit var target: String

    override fun check(client: DaemonClient): ModelCheckResponse = client.checkModel(target, limit)
}
