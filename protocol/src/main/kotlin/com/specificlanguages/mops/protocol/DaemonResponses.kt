package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Common contract for all daemon protocol responses. The wire `type` discriminator is derived from each leaf's
 * [SerialName].
 */
@Serializable
sealed interface DaemonResponse

/**
 * Structured failure response returned instead of throwing protocol-level exceptions across the socket.
 */
@Serializable
@SerialName("error")
data class DaemonErrorResponse(
    val errorCode: String,
    val message: String,
    val workspacePath: String?,
) : DaemonResponse

/**
 * Successful ping response and runtime metadata for the owning project daemon.
 */
@Serializable
@SerialName("pong")
data class PongResponse(
    val projectPath: String,
    val mpsHome: String,
    val workspacePath: String,
) : DaemonResponse

@Serializable
@SerialName("stop")
class StoppedResponse : DaemonResponse {
    // Required while we have no other data in the class
    override fun equals(other: Any?): Boolean = other is StoppedResponse
    override fun hashCode(): Int = javaClass.hashCode()
    override fun toString(): String = "StopResponse"
}

/**
 * Successful response for a completed or already-current model resave operation.
 */
@Serializable
@SerialName("model-resave")
data class ModelResaveResponse(val modelTarget: String) : DaemonResponse

/**
 * Successful response carrying one JSON node export object.
 */
@Serializable
@SerialName("model-get-node")
data class ModelGetNodeResponse(val node: MpsNodeJson) : DaemonResponse

/**
 * Successful response carrying bounded Node Usage search results.
 */
@Serializable
@SerialName("usages")
data class FindUsagesResponse(
    val limit: Int,
    val truncated: Boolean,
    val usages: List<MpsNodeUsageJson>,
) : DaemonResponse

/**
 * Successful response carrying bounded concept instance search results.
 */
@Serializable
@SerialName("nodes")
data class FindInstancesResponse(
    val limit: Int,
    val truncated: Boolean,
    val nodes: List<MpsNodeSummaryJson>,
) : DaemonResponse

/**
 * Successful response for an applied Edit Operation batch.
 */
@Serializable
@SerialName("model-edit")
data class ModelEditResponse(
    val created: Map<String, String>,
    val violations: List<EditConstraintViolation>,
    // Non-fatal notices, e.g. constraints skipped because a concept's language was not loaded. One line per language,
    // capped, with a final summary line when more languages were affected.
    val warnings: List<String> = emptyList(),
) : DaemonResponse

/**
 * Successful response carrying a semantic list tree rooted at the resolved MPS navigation target.
 */
@Serializable
@SerialName("list")
data class MpsListResponse(val root: MpsListEntryJson) : DaemonResponse

/**
 * Startup message emitted on daemon stdout when the loopback server is ready to accept authenticated requests.
 */
@Serializable
@SerialName("ready")
data class ReadyMessage(val port: Int) : DaemonResponse
