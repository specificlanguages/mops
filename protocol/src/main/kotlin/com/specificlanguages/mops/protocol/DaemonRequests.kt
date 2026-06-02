package com.specificlanguages.mops.protocol

/**
 * Base contract for authenticated requests sent over the daemon socket.
 */
sealed interface DaemonRequest {
    val type: String
    val token: String
}

data class PingRequest(override val token: String) : DaemonRequest {
    override val type: String = "ping"
}

data class StopRequest(override val token: String) : DaemonRequest {
    override val type: String = "stop"
}

/**
 * Request to resave one model target inside the project daemon.
 */
data class ModelResaveRequest(
    override val token: String,
    val modelTarget: String?,
) : DaemonRequest {
    override val type: String = "model-resave"
}

/**
 * Request to export one node from the project daemon.
 */
data class ModelGetNodeRequest(
    override val token: String,
    val target: NodeTarget,
) : DaemonRequest {
    override val type: String = "model-get-node"
}

/**
 * Request to find references to one resolved MPS node in editable project sources.
 */
data class FindUsagesRequest(
    override val token: String,
    val target: NodeTarget,
    val limit: Int,
) : DaemonRequest {
    override val type: String = "find-usages"
}

/**
 * Request to list one MPS navigation target as a bounded semantic tree.
 */
data class MpsListRequest(
    override val token: String,
    val target: List<String>?,
    val depth: Int,
) : DaemonRequest {
    override val type: String = "list"
}
