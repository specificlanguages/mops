package com.specificlanguages.mops.daemon

data class CodeTypeMembers(
    val type: String,
    val hierarchy: List<String>,
    val methods: List<CodeMethodMember>,
    val properties: List<CodePropertyMember>,
    val fields: List<CodeFieldMember>,
)

data class CodeMemberParameter(val name: String?, val type: String)

data class CodeMethodMember(
    val name: String,
    val parameters: List<CodeMemberParameter>,
    val returnType: String,
    val declaringClass: String,
    val inherited: Boolean,
    val static: Boolean,
)

data class CodePropertyMember(
    val name: String,
    val type: String,
    val getter: String?,
    val setter: String?,
    val declaringClass: String,
    val inherited: Boolean,
)

data class CodeFieldMember(
    val name: String,
    val type: String,
    val declaringClass: String,
    val inherited: Boolean,
    val static: Boolean,
    val writable: Boolean,
)
