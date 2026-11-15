package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Why one module could not be loaded, as a node in a dependency tree. [causes] is populated only for
 * [ModuleLoadProblemReason.BROKEN_DEPENDENCIES]. [fix] makes [module] load, or is null when mops knows no such fix. A
 * problem with [causes] has no fix: a fix of a root cause makes that root module load, but mops does not know that
 * this makes [module] load too.
 */
@Serializable
data class ModuleLoadProblemJson(
    val module: String,
    val reason: ModuleLoadProblemReason,
    val detail: String? = null,
    val fix: ModuleLoadFix? = null,
    val causes: List<ModuleLoadProblemJson> = emptyList(),
) {
    /**
     * The leaves of this problem tree, which are the root modules to fix: this problem itself when it has no
     * [causes]. A module reached through several dependency paths is listed once.
     */
    val rootCauses: List<ModuleLoadProblemJson>
        get() = leaves().distinctBy { it.module to it.reason }
    private fun leaves(): List<ModuleLoadProblemJson> =
        if (causes.isEmpty()) listOf(this) else causes.flatMap { it.leaves() }
}
