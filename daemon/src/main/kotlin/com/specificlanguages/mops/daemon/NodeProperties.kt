package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil

/**
 * Live access to native property values by name, including inherited properties. Iterates the descriptors of set
 * properties like native `SNode.getProperties()`, which it shadows in Groovy.
 */
class NodeProperties(private val node: SNode) : Iterable<SProperty> {
    override fun iterator(): Iterator<SProperty> {
        requireRead("SNode.properties")
        return node.properties.iterator()
    }

    fun propertyMissing(name: String): Nothing = missingMember(name)
    fun propertyMissing(name: String, @Suppress("unused") value: Any?): Nothing = missingMember(name)
    fun methodMissing(name: String, @Suppress("unused") arguments: Any?): Nothing = missingMember("$name()")

    private fun missingMember(member: String): Nothing = missingAccessorMember("properties", "name", member, "node.concept.properties")

    @Suppress("DEPRECATION")
    fun getAt(name: String): String? {
        requireRead("SNode.properties[]")
        val property = node.concept.properties.firstOrNull { it.name == name } ?: return null
        return SNodeAccessUtil.getProperty(node, property)
    }

    /** Passes a serialized property value through MPS property setters; null requests clearing. */
    @Suppress("DEPRECATION")
    fun putAt(name: String, value: Any?) {
        requireCommand("SNode.properties[] assignment")
        require(value == null || value is CharSequence) { "property value must be a string or null" }
        val property = node.concept.properties.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("unknown property: $name")
        SNodeAccessUtil.setProperty(node, property, value?.toString())
    }
}
