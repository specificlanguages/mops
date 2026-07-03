package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How `mops model edit` treats **Constraints** and concepts it cannot check.
 *
 * - [ADVISORY]: evaluate constraints and report violations, but apply and save the batch anyway.
 * - [BEST_EFFORT]: evaluate constraints; a violation blocks the batch (nothing is saved). A concept whose language did
 *   not load cannot be checked, so it is skipped and reported as a warning rather than failing.
 * - [STRICT]: like [BEST_EFFORT] for violations, but a concept that cannot be checked aborts the batch.
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
