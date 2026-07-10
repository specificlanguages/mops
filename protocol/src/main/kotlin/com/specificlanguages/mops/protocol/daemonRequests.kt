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
 * Request to find references to one resolved MPS node. Searches editable project sources by default; an explicit
 * [scope] (the raw navigation-target segments of an `in` clause) is searched exhaustively, including read-only library
 * and stub models within it. `["/"]` names the whole repository.
 */
@Serializable
@SerialName("find-usages")
data class FindUsagesRequest(
    override val token: String,
    val target: NodeTarget,
    val limit: Int,
    val scope: List<String>? = null,
) : DaemonRequest

/**
 * Request to find instances of one MPS concept. Searches editable project sources by default; an explicit [scope] (the
 * raw navigation-target segments of an `in` clause) is searched exhaustively, including read-only library and stub
 * models within it. `["/"]` names the whole repository.
 */
@Serializable
@SerialName("find-instances")
data class FindInstancesRequest(
    override val token: String,
    val concept: String,
    val exact: Boolean,
    val limit: Int,
    val scope: List<String>? = null,
) : DaemonRequest

/**
 * Request to find root nodes whose name matches [pattern], using MPS's Go-to-Node name-pattern matching. Searches
 * editable project sources by default; an explicit [scope] (the raw navigation-target segments of an `in` clause) is
 * searched exhaustively, including read-only library and stub models within it. `["/"]` names the whole repository.
 * Only root-bearing scopes are valid: a node or root-node scope is rejected because it holds no Root Nodes.
 */
@Serializable
@SerialName("find-by-name")
data class FindByNameRequest(
    override val token: String,
    val pattern: String,
    val limit: Int,
    val scope: List<String>? = null,
) : DaemonRequest

/**
 * Request to apply one batch of Edit Operations as a unit inside the project daemon, with best-effort atomicity.
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

/**
 * Request to diagnose the load state of the project's languages and Java-bearing modules: which are loaded, and for
 * each unloaded one, why.
 */
@Serializable
@SerialName("diagnose-modules")
data class DiagnoseModulesRequest(override val token: String) : DaemonRequest

/**
 * Request to diagnose the load state of one module addressed by [module] (a module name or serialized module
 * reference), including whether it is present in the repository at all.
 */
@Serializable
@SerialName("diagnose-module")
data class DiagnoseModuleRequest(
    override val token: String,
    val module: String,
) : DaemonRequest

/**
 * Request to run the MPS make (generation and compilation) on the named [modules] (each a module name or serialized
 * module reference) together with their transitive dependency closure, so that any un-made dependency is made too.
 */
@Serializable
@SerialName("make-modules")
data class MakeModulesRequest(
    override val token: String,
    val modules: List<String>,
) : DaemonRequest

/**
 * Request to run the MPS make (generation and compilation) on every generatable module in the project.
 */
@Serializable
@SerialName("make-project")
data class MakeProjectRequest(override val token: String) : DaemonRequest
