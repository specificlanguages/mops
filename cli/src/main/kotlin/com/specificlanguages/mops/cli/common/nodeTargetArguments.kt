package com.specificlanguages.mops.cli.common

import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.argumentReading

/**
 * Reads the two-argument node target form, MODEL_TARGET NODE_ID. A model target is a model name or a serialized model
 * reference and has no `/`, so a first argument shaped like a serialized node reference (`r:...(...)/id`) is rejected:
 * two node references were passed where the command takes one node.
 */
fun modelTargetAndNodeId(modelTarget: String, nodeId: String): NodeTarget.InModel {
    require(!(modelTarget.startsWith("r:") && modelTarget.contains('/'))) {
        argumentReading(modelTarget to "MODEL_TARGET", nodeId to "NODE_ID") +
            ", but `$modelTarget` is a serialized node reference, not a model target: pass one node reference per call"
    }
    return NodeTarget.InModel(modelTarget = modelTarget, nodeId = nodeId)
}
