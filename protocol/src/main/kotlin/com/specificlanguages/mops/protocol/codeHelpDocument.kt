package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable
data class CodeHelpDocument(
    val index: List<String> = emptyList(),
    val entries: List<CodeHelpEntry> = emptyList(),
    val nativeType: CodeHelpNativeType? = null,
)

@Serializable
data class CodeHelpEntry(
    val path: String,
    val signature: String,
    val summary: String,
    val access: String,
    val parameters: List<CodeHelpParameter> = emptyList(),
    val returnType: String,
    val nullable: Boolean = false,
    val options: List<CodeHelpOption> = emptyList(),
    val examples: List<String> = emptyList(),
    val related: List<String> = emptyList(),
    val readAccess: String? = null,
    val writeAccess: String? = null,
)

@Serializable
data class CodeHelpParameter(val name: String, val type: String, val nullable: Boolean = false, val default: String? = null, val description: String? = null)

@Serializable
data class CodeHelpOption(val name: String, val type: String, val default: String, val description: String, val values: List<String> = emptyList())

@Serializable
data class CodeHelpNativeType(val type: String, val hierarchy: List<String>, val methods: List<CodeHelpNativeMethod>, val properties: List<CodeHelpNativeProperty>, val fields: List<CodeHelpNativeField>)

@Serializable
data class CodeHelpNativeMethod(val name: String, val parameters: List<CodeHelpParameter>, val returnType: String, val declaringClass: String, val inherited: Boolean, val static: Boolean)

@Serializable
data class CodeHelpNativeProperty(val name: String, val type: String, val getter: String?, val setter: String?, val declaringClass: String, val inherited: Boolean, val shadowedBy: List<String> = emptyList())

@Serializable
data class CodeHelpNativeField(val name: String, val type: String, val declaringClass: String, val inherited: Boolean, val static: Boolean, val writable: Boolean)
