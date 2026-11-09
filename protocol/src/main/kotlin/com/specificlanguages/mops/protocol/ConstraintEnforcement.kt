package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How `mops edit model` treats **Constraints** and concepts it cannot check. `mops edit model --help` describes each
 * mode under `--constraints`.
 */
@Serializable
enum class ConstraintEnforcement {
    @SerialName("advisory")
    ADVISORY,

    @SerialName("best-effort")
    BEST_EFFORT,

    @SerialName("strict")
    STRICT,

    ;

    companion object {
        /** Maps a CLI/wire spelling (`advisory`, `best-effort`, `strict`) to its enum value, or null if unknown. */
        fun fromWireName(value: String): ConstraintEnforcement? =
            when (value) {
                "advisory" -> ADVISORY
                "best-effort" -> BEST_EFFORT
                "strict" -> STRICT
                else -> null
            }
    }
}
