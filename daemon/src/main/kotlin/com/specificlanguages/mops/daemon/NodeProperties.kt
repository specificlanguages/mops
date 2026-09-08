package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode

/** Live access to native property values by name, including inherited properties. */
class NodeProperties(private val node: SNode) {
    fun getAt(name: String): String? {
        requireRead("SNode.properties[]")
        val property = node.concept.properties.firstOrNull { it.name == name } ?: return null
        return node.getProperty(property)
    }

    /** Sets a raw MPS property value; null clears it. Native property constraints are not checked. */
    fun putAt(name: String, value: Any?) {
        requireCommand("SNode.properties[] assignment")
        require(value == null || value is CharSequence) { "property value must be a string or null" }
        val property = node.concept.properties.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("unknown property: $name")
        node.setProperty(property, value?.toString())
    }
}
