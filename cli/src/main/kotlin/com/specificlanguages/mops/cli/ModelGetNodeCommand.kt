package com.specificlanguages.mops.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(name = "get-node", description = ["Export one loaded MPS node as JSON."])
class ModelGetNodeCommand : Runnable {
    @Parameters(
        index = "0..1",
        arity = "1..2",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET NODE_ID",
        description = ["Serialized node reference, or model target followed by node ID."],
    )
    lateinit var nodeTarget: Array<String>

    override fun run() {
        throw UnsupportedOperationException("model get-node is not implemented yet")
    }
}
