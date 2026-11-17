package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A **Node Target**: the node a lookup names, either as a model target and a node ID given apart, or as one string.
 */
@Serializable
sealed interface NodeTarget {
    @Serializable
    @SerialName("inModel")
    data class InModel(val modelTarget: String, val nodeId: String) : NodeTarget

    /** A **Node Target** written as one string. */
    @Serializable
    @SerialName("nodeReference")
    data class SingleString(@SerialName("nodeReference") val text: String) : NodeTarget
}
