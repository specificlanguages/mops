package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What `mops edit model` does with a **Constraint** violation. `mops edit model --help` describes each mode under
 * `--constraints`.
 *
 * What happens to a concept that cannot be checked because its language did not load is a separate setting,
 * [ConstraintPolicy.ignoreMissingLanguages].
 */
@Serializable
enum class ConstraintMode {
    @SerialName("advisory")
    ADVISORY,

    @SerialName("strict")
    STRICT,

    ;

    companion object {
        /** Maps a CLI/wire spelling (`advisory`, `strict`) to its enum value, or null if unknown. */
        fun fromWireName(value: String): ConstraintMode? =
            when (value) {
                "advisory" -> ADVISORY
                "strict" -> STRICT
                else -> null
            }
    }
}
