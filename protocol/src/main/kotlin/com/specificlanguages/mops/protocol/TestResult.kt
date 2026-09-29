package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable
data class TestResult(
    val id: String,
    val name: String,
    val kind: String,
    val status: String,
    val source: String? = null,
    val className: String? = null,
    val methodName: String? = null,
    val detail: String? = null,
)
