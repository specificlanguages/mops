package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable
data class MpsNodeJson(
    val model: String? = null,
    val role: String? = null,
    val concept: String,
    val id: String? = null,
    val properties: List<MpsNodePropertyJson>? = null,
    val references: List<MpsNodeReferenceJson>? = null,
    val children: List<MpsNodeJson>? = null,
)

@Serializable
data class MpsNodePropertyJson(
    val name: String,
    val value: String,
)

@Serializable
data class MpsNodeReferenceJson(
    val role: String,
    val target: MpsNodeReferenceTargetJson,
)

@Serializable
data class MpsNodeReferenceTargetJson(
    val model: String? = null,
    val node: String? = null,
    val name: String? = null,
    val concept: String? = null,
)
