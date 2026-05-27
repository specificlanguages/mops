package com.specificlanguages.mops.protocol

data class MpsListEntryJson(
    val type: String,
    val name: String?,
    val moduleKind: String? = null,
    val role: String? = null,
    val concept: String? = null,
    val id: String? = null,
    val reference: String? = null,
    val error: String? = null,
    val children: List<MpsListEntryJson>? = null,
)
