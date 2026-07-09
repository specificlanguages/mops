package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Outcome of a make run.
 *
 * MPS's own make result is not trustworthy on its own — it can report success while errors were reported — so
 * [SUCCESS] means the make result was successful *and* no error message was seen; any reported error yields [FAILED].
 */
@Serializable
enum class MakeOutcome {
    /** The make finished and reported no errors. */
    SUCCESS,

    /** The make result was a failure, or at least one error message was reported. */
    FAILED,

    /** The make set held no generatable model, so nothing ran. */
    NOTHING_TO_GENERATE,
}
