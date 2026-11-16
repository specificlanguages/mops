package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * A fix for a module load problem or an unusable language: make [makeModules], and with them their dependency
 * closure, with `mops make module`.
 */
@Serializable
data class ModuleLoadFix(val makeModules: List<String>) {
    init {
        require(makeModules.isNotEmpty()) { "a fix makes at least one module" }
    }

    /** The command line that applies this fix. */
    val command: String
        get() = "mops make module ${makeModules.joinToString(" ")}"

    /** How every message and text output names this fix: `fix: <command>`. */
    val label: String
        get() = "fix: $command"

    companion object {
        /**
         * One fix that applies all of [fixes], or null when [fixes] is empty or any of them is null, that is, when
         * some problem has no known fix and applying the others would not be enough.
         */
        fun combine(fixes: List<ModuleLoadFix?>): ModuleLoadFix? =
            if (fixes.isEmpty() || fixes.any { it == null }) {
                null
            } else {
                ModuleLoadFix(fixes.flatMap { it!!.makeModules }.distinct())
            }
    }
}
