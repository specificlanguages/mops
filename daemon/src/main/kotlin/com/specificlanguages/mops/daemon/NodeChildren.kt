package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.SNode
import java.util.Collections

/**
 * Live indexed access to children by role name or by [SContainmentLink] descriptor, without declared cardinality or constraint checks. Iterates all children
 * across roles like native `SNode.getChildren()`, which it shadows in Groovy.
 */
class NodeChildren(private val node: SNode) : NodeAccessor<SNode>("children", "role", "getChildren(containmentLink)") {
    override fun iterator(): Iterator<SNode> {
        requireRead("SNode.children")
        return node.children.iterator()
    }

    fun getAt(role: String): List<SNode> {
        requireRead("SNode.children[]")
        return read(node.requireContainmentLink(role))
    }

    fun getAt(link: SContainmentLink): List<SNode> {
        requireRead("SNode.children[]")
        return read(node.requireContainmentLink(link))
    }

    /** Replaces the role's children in list order. New children must be detached; existing children may be retained. */
    fun putAt(role: String, value: Any?) {
        requireCommand("SNode.children[] assignment")
        write(node.requireContainmentLink(role), value)
    }

    /** Replaces the role's children in list order. New children must be detached; existing children may be retained. */
    fun putAt(link: SContainmentLink, value: Any?) {
        requireCommand("SNode.children[] assignment")
        write(node.requireContainmentLink(link), value)
    }

    private fun read(link: SContainmentLink): List<SNode> = Collections.unmodifiableList(node.getChildren(link).toList())

    private fun write(link: SContainmentLink, value: Any?) {
        require(value is List<*> && value.all { it is SNode }) { "children must be a list of nodes" }
        val children = value.map { it as SNode }
        val role = link.name
        val existing = node.getChildren(link).toList()
        val identities = Collections.newSetFromMap(java.util.IdentityHashMap<SNode, Boolean>())
        children.forEach { child ->
            require(identities.add(child)) { "duplicate child in role: $role" }
            require(existing.any { it === child } || (child.parent == null && child.model == null)) {
                "child must be detached or already belong to this role: $role"
            }
            require(generateSequence(node) { it.parent }.none { it === child }) { "child would create a containment cycle" }
        }
        existing.forEach(node::removeChild)
        children.forEach { node.addChild(link, it) }
    }
}
