package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable
data class InstanceConceptJson(
    val name: String,
    val id: String,
    val valid: Boolean,
)
