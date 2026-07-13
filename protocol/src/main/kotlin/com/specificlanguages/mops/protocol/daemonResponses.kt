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
 * Successful response carrying one JSON node export object.
 */
@Serializable
@SerialName("model-get-node")
data class ModelGetNodeResponse(val node: MpsNodeJson) : DaemonResponse

/**
 * Successful response carrying the plain-text rendering of one node's default editor. The [text] preserves the editor's
 * line breaks and indentation; it rides the single-line protocol as an escaped JSON string.
 */
@Serializable
@SerialName("model-render-node")
data class ModelRenderNodeResponse(val text: String) : DaemonResponse

/**
 * Successful response carrying the findings from a **Model Check** over one model, ordered most severe first and bounded
 * by the request's limit. [truncated] is true when findings beyond [limit] were dropped. [totals] counts every finding
 * by severity before truncation, so a caller can report the full picture even when only [findings] was sent.
 */
@Serializable
@SerialName("model-check")
data class ModelCheckResponse(
    val limit: Int,
    val truncated: Boolean,
    val totals: ModelCheckFindingCounts,
    val findings: List<ModelCheckFindingJson>,
) : DaemonResponse

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
 * Successful response carrying bounded name-pattern search results, ordered best match first.
 */
@Serializable
@SerialName("named-nodes")
data class FindByNameResponse(
    val limit: Int,
    val truncated: Boolean,
    val nodes: List<MpsNodeSummaryJson>,
) : DaemonResponse

/**
 * Successful response carrying bounded node-by-id search results — one per model whose [Node ID][FindNodeByIdRequest]
 * matched. A well-formed id matching nothing yields an empty [nodes] list, which is a success, not a failure.
 */
@Serializable
@SerialName("nodes-by-id")
data class FindNodeByIdResponse(
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
 * Successful response carrying the load diagnosis for the project's diagnosable modules: the [summary] counts plus one
 * [ModuleLoadDiagnosticJson] per module (all languages and every other project module with a Java facet), ordered by
 * module name.
 */
@Serializable
@SerialName("module-diagnostics")
data class ModulesDiagnosticsResponse(
    val summary: ModuleLoadSummary,
    val modules: List<ModuleLoadDiagnosticJson>,
) : DaemonResponse

/**
 * Successful response carrying the load diagnosis for one requested module.
 */
@Serializable
@SerialName("module-diagnostic")
data class ModuleDiagnosticResponse(
    val module: ModuleLoadDiagnosticJson,
) : DaemonResponse

/**
 * Successful response carrying the result of a make run: its [outcome], how many generatable modules were in the make
 * set (the requested modules plus their generatable dependency closure), and the error and warning [messages] MPS
 * reported. On [MakeOutcome.FAILED] the [messages] carry the errors; on [MakeOutcome.NOTHING_TO_GENERATE] the make set
 * held no generatable model.
 */
@Serializable
@SerialName("make-result")
data class MakeResponse(
    val outcome: MakeOutcome,
    val moduleCount: Int,
    val messages: List<MakeMessageJson>,
) : DaemonResponse

/**
 * Startup message emitted on daemon stdout when the loopback server is ready to accept authenticated requests.
 */
@Serializable
@SerialName("ready")
data class ReadyMessage(val port: Int) : DaemonResponse
