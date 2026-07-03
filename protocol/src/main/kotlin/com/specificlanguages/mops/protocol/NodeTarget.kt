package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Target of a node lookup: either a node inside a model, or a serialized node reference.
 */
@Serializable
sealed interface NodeTarget {
    @Serializable
    @SerialName("inModel")
    data class InModel(val modelTarget: String, val nodeId: String) : NodeTarget

    @Serializable
    @SerialName("nodeReference")
    data class NodeReference(val nodeReference: String) : NodeTarget
}
