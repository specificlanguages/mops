package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base contract for authenticated requests sent over the daemon socket. The wire `type` discriminator is derived from
 * each leaf's [SerialName].
 */
@Serializable
sealed interface DaemonRequest {
    val token: String
}

@Serializable
@SerialName("ping")
data class PingRequest(override val token: String) : DaemonRequest

@Serializable
@SerialName("stop")
data class StopRequest(override val token: String) : DaemonRequest

/**
 * Request to resave one model target inside the project daemon.
 */
@Serializable
@SerialName("model-resave")
data class ModelResaveRequest(
    override val token: String,
    val modelTarget: String,
) : DaemonRequest

/**
 * Request to export one node from the project daemon.
 */
@Serializable
@SerialName("model-get-node")
data class ModelGetNodeRequest(
    override val token: String,
    val target: NodeTarget,
    // When set, the exported node carries its full containment chain up to the Root Node; otherwise only the immediate
    // parent.
    val ancestry: Boolean = false,
) : DaemonRequest

/**
 * Request to find references to one resolved MPS node. Searches editable project sources by default, or the whole
 * repository (including read-only library and stub models) when [all] is set.
 */
@Serializable
@SerialName("find-usages")
data class FindUsagesRequest(
    override val token: String,
    val target: NodeTarget,
    val limit: Int,
    val all: Boolean = false,
) : DaemonRequest

/**
 * Request to find instances of one MPS concept. Searches editable project sources by default, or the whole repository
 * (including read-only library and stub models) when [all] is set.
 */
@Serializable
@SerialName("find-instances")
data class FindInstancesRequest(
    override val token: String,
    val concept: String,
    val exact: Boolean,
    val limit: Int,
    val all: Boolean = false,
) : DaemonRequest

/**
 * Request to find root nodes whose name matches [pattern], using MPS's Go-to-Node name-pattern matching. Searches
 * editable project sources by default, or the whole repository (including read-only library and stub models) when
 * [all] is set.
 */
@Serializable
@SerialName("find-by-name")
data class FindByNameRequest(
    override val token: String,
    val pattern: String,
    val limit: Int,
    val all: Boolean = false,
) : DaemonRequest

/**
 * Request to apply one atomic batch of Edit Operations inside the project daemon.
 */
@Serializable
@SerialName("model-edit")
data class ModelEditRequest(
    override val token: String,
    val batch: EditBatch,
    val constraints: ConstraintEnforcement = ConstraintEnforcement.BEST_EFFORT,
) : DaemonRequest

/**
 * Request to list one MPS navigation target as a bounded semantic tree.
 */
@Serializable
@SerialName("list")
data class MpsListRequest(
    override val token: String,
    val target: List<String>? = null,
    val depth: Int,
) : DaemonRequest
