package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode

/** Single-child view of a role, independent of its declared cardinality. */
class NodeChild(node: SNode) {
    private val children = NodeChildren(node)

    fun getAt(role: String): SNode? {
        val values = children.getAt(role)
        check(values.size <= 1) { "child role '$role' has ${values.size} children; expected at most one" }
        return values.firstOrNull()
    }

    /** Replaces all children of the role with one child, or clears the role with null. */
    fun putAt(role: String, value: Any?) {
        requireCommand("SNode.child[] assignment")
        require(value == null || value is SNode) { "child must be a node or null" }
        children.putAt(role, listOfNotNull(value))
    }
}
