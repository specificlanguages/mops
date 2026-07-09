package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * One end-user-facing message reported by MPS during a make: an error or a warning, with its text. Information-level
 * make messages (progress, timing) are not carried back.
 */
@Serializable
data class MakeMessageJson(
    val kind: MakeMessageKind,
    val text: String,
)

/**
 * Severity of a [MakeMessageJson]. Mirrors the subset of MPS `MessageKind` values worth reporting from a make.
 */
@Serializable
enum class MakeMessageKind {
    ERROR,
    WARNING,
}
