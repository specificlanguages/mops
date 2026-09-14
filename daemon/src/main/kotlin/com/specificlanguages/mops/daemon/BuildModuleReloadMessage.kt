package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode

class BuildModuleReloadMessage(
    val kind: String,
    val text: String,
    val node: SNode?,
) : AbstractMap<String, Any?>() {
    override val entries: Set<Map.Entry<String, Any?>> = mapOf(
        "kind" to kind,
        "text" to text,
        "node" to node,
    ).entries
}
