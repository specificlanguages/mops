package com.specificlanguages.mops.protocol

/**
 * Common fields for all daemon protocol responses.
 */
sealed interface DaemonResponse {
    val type: String
}

/**
 * Structured failure response returned instead of throwing protocol-level exceptions across the socket.
 */
data class DaemonErrorResponse(
    val errorCode: String,
    val message: String,
    val workspacePath: String?,
) : DaemonResponse {
    override val type: String = "error"
}

/**
 * Successful ping response and runtime metadata for the owning project daemon.
 */
data class PongResponse(
    val projectPath: String,
    val mpsHome: String,
    val workspacePath: String,
) : DaemonResponse {
    override val type: String = "pong"
}

class StoppedResponse : DaemonResponse {
    override val type: String = "stop"

    // Required while we have no other data in the class
    override fun equals(other: Any?): Boolean = other is StoppedResponse
    override fun hashCode(): Int = javaClass.hashCode()
    override fun toString(): String = "StopResponse"
}

/**
 * Successful response for a completed or already-current model resave operation.
 */
data class ModelResaveResponse(val modelTarget: String) : DaemonResponse {
    override val type: String = "model-resave"
}

/**
 * Successful response carrying one JSON node export object.
 */
data class ModelGetNodeResponse(val node: MpsNodeJson) : DaemonResponse {
    override val type: String = "model-get-node"
}

/**
 * Successful response carrying bounded Node Usage search results.
 */
data class FindUsagesResponse(
    val limit: Int,
    val truncated: Boolean,
    val usages: List<MpsNodeUsageJson>,
) : DaemonResponse {
    override val type: String = "usages"
}

/**
 * Successful response carrying a semantic list tree rooted at the resolved MPS navigation target.
 */
data class MpsListResponse(val root: MpsListEntryJson) : DaemonResponse {
    override val type: String = "list"
}

/**
 * Startup message emitted on daemon stdout when the loopback server is ready to accept authenticated requests.
 */
data class ReadyMessage(val port: Int) : DaemonResponse {
    override val type: String = "ready"
}
