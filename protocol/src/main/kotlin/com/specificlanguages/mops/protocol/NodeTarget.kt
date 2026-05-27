package com.specificlanguages.mops.protocol

sealed interface NodeTarget {
    data class InModel(val modelTarget: String, val nodeId: String) : NodeTarget
    data class NodeReference(val nodeReference: String) : NodeTarget
}
