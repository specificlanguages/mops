package com.specificlanguages.mops.daemoncomms

sealed interface GetNodeTarget {
    data class InModel(val modelTarget: String, val nodeId: String) : GetNodeTarget
    data class NodeReference(val nodeReference: String) : GetNodeTarget
}
