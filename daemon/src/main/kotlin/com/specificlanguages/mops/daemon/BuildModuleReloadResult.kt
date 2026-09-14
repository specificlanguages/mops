package com.specificlanguages.mops.daemon

class BuildModuleReloadResult(
    val succeeded: Boolean,
    val messages: List<BuildModuleReloadMessage>,
) : AbstractMap<String, Any?>() {
    override val entries: Set<Map.Entry<String, Any?>> = mapOf(
        "succeeded" to succeeded,
        "messages" to messages,
    ).entries
}
