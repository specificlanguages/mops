@file:OptIn(ExperimentalSerializationApi::class)

package com.specificlanguages.mops.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class MpsListEntryJson(
    val type: String,
    val name: String?,
    val moduleKind: String? = null,
    val role: String? = null,
    val concept: String? = null,
    // False when a node/root entry's concept could not be resolved (its language is not loaded); omitted otherwise.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conceptValid: Boolean = true,
    val id: String? = null,
    val reference: String? = null,
    val error: String? = null,
    val children: List<MpsListEntryJson>? = null,
)
