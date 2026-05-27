package com.specificlanguages.mops.protocol

data class MpsNodeJson(
    val model: String? = null,
    val role: String? = null,
    val concept: String,
    val id: String,
    val properties: List<MpsNodePropertyJson>? = null,
    val references: List<MpsNodeReferenceJson>? = null,
    val children: List<MpsNodeJson>? = null,
)

data class MpsNodePropertyJson(
    val name: String,
    val value: String,
)

data class MpsNodeReferenceJson(
    val role: String,
    val target: MpsNodeReferenceTargetJson,
)

data class MpsNodeReferenceTargetJson(
    val model: String? = null,
    val node: String? = null,
)
