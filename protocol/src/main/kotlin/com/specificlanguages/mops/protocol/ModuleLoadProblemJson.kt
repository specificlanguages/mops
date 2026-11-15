package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Why one module could not be loaded, as a node in a dependency tree. [causes] is populated only for
 * [ModuleLoadProblemReason.BROKEN_DEPENDENCIES]. [fix] is the fix for this problem itself, or null when mops knows
 * none; a problem with [causes] has no fix of its own, since its [rootCauses] carry them.
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

    /** The fix that resolves every root cause, or null when some root cause has no known fix. */
    val completeFix: ModuleLoadFix?
        get() = ModuleLoadFix.combine(rootCauses.map { it.fix })

    private fun leaves(): List<ModuleLoadProblemJson> =
        if (causes.isEmpty()) listOf(this) else causes.flatMap { it.leaves() }
}
