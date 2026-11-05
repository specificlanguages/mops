package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * One end-user-facing message reported by MPS during a make, with its kind and text. Information messages are carried
 * back only when the make request sets a message level.
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
    INFO,
}
