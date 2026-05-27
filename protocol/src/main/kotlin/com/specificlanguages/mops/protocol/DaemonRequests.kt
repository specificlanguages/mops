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
 * Request to resave one model target inside the already loaded project daemon.
 */
data class ModelResaveRequest(
    override val token: String,
    val modelTarget: String?,
) : DaemonRequest {
    override val type: String = "model-resave"
}

/**
 * Request to export one loaded node from the project daemon.
 */
data class ModelGetNodeRequest(
    override val token: String,
    val target: GetNodeTarget,
) : DaemonRequest {
    override val type: String = "model-get-node"
}
