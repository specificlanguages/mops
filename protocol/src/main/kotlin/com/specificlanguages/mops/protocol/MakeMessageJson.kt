package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * One end-user-facing message reported by MPS during a make, with its kind and text. Which kinds are carried back
 * depends on the make request's message level; without one, information messages are left out.
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
