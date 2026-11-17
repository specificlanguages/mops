package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SNode

/**
 * Live access to native property values by name or by [SProperty] descriptor, including inherited properties. Iterates the descriptors of set
 * properties like native `SNode.getProperties()`, which it shadows in Groovy.
 */
class NodeProperties(private val node: SNode) : NodeAccessor<SProperty>("properties", "name", "node.concept.properties") {
    override fun iterator(): Iterator<SProperty> {
        requireRead("SNode.properties")
        return node.properties.iterator()
    }

    /** Reads a property value through MPS property getters; see [readPropertyText]. */
    fun getAt(name: String): String? {
        requireRead("SNode.properties[]")
        return readPropertyText(node, node.requireProperty(name))
    }

    /** Reads a property value through MPS property getters; see [readPropertyText]. */
    fun getAt(property: SProperty): String? {
        requireRead("SNode.properties[]")
        return readPropertyText(node, node.requireProperty(property))
    }

    /** Passes a property value through MPS property setters; null requests clearing. See [writePropertyText]. */
    fun putAt(name: String, value: Any?) {
        requireCommand("SNode.properties[] assignment")
        write(node.requireProperty(name), value)
    }

    /** Passes a property value through MPS property setters; null requests clearing. See [writePropertyText]. */
    fun putAt(property: SProperty, value: Any?) {
        requireCommand("SNode.properties[] assignment")
        write(node.requireProperty(property), value)
    }

    private fun write(property: SProperty, value: Any?) {
        require(value == null || value is CharSequence) { "property value must be a string or null" }
        writePropertyText(node, property, value?.toString())
    }
}
