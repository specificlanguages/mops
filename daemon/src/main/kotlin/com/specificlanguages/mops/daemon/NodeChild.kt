package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.SNode

/** Single-child view of a role, independent of its declared cardinality. */
class NodeChild(node: SNode) {
    private val children = NodeChildren(node)

    fun getAt(role: String): SNode? = single(role, children.getAt(role))

    fun getAt(link: SContainmentLink): SNode? = single(link.name, children.getAt(link))

    /** Replaces all children of the role with one child, or clears the role with null. */
    fun putAt(role: String, value: Any?) {
        requireCommand("SNode.child[] assignment")
        children.putAt(role, childList(value))
    }

    /** Replaces all children of the role with one child, or clears the role with null. */
    fun putAt(link: SContainmentLink, value: Any?) {
        requireCommand("SNode.child[] assignment")
        children.putAt(link, childList(value))
    }

    private fun single(role: String, values: List<SNode>): SNode? {
        check(values.size <= 1) { "child role '$role' has ${values.size} children; expected at most one" }
        return values.firstOrNull()
    }

    private fun childList(value: Any?): List<SNode> {
        require(value == null || value is SNode) { "child must be a node or null" }
        return listOfNotNull(value as SNode?)
    }
}
