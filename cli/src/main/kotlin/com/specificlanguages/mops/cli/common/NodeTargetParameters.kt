package com.specificlanguages.mops.cli.common

import com.specificlanguages.mops.protocol.NodeTarget
import picocli.CommandLine.Parameters

/**
 * Positional parameters of commands that address one node: either a serialized node reference, or a model target
 * followed by a node ID. Commands include it with `@Mixin` and spell out both forms in their `customSynopsis`.
 */
class NodeTargetParameters {
    @Parameters(
        index = "0",
        paramLabel = "NODE_REFERENCE | MODEL_TARGET",
        description = ["Serialized node reference, or the model target of NODE_ID."],
    )
    lateinit var nodeReferenceOrModelTarget: String

    @Parameters(
        index = "1",
        arity = "0..1",
        paramLabel = "NODE_ID",
        description = ["Node ID within MODEL_TARGET, in either the decimal or the encoded spelling."],
    )
    var nodeId: String? = null

    fun target(): NodeTarget =
        nodeId?.let { NodeTarget.InModel(modelTarget = nodeReferenceOrModelTarget, nodeId = it) }
            ?: NodeTarget.NodeReference(nodeReferenceOrModelTarget)
}
