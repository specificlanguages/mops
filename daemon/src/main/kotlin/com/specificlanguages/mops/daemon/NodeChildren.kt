package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode
import java.util.Collections

/** Live indexed access to children by role, without declared cardinality or constraint checks. */
class NodeChildren(private val node: SNode) {
    fun getAt(role: String): List<SNode> {
        requireRead("SNode.children[]")
        val link = node.concept.containmentLinks.firstOrNull { it.name == role } ?: return emptyList()
        return Collections.unmodifiableList(node.getChildren(link).toList())
    }

    /** Replaces the role's children in list order. New children must be detached; existing children may be retained. */
    fun putAt(role: String, value: Any?) {
        requireCommand("SNode.children[] assignment")
        require(value is List<*> && value.all { it is SNode }) { "children must be a list of nodes" }
        val children = value.map { it as SNode }
        val link = node.concept.containmentLinks.firstOrNull { it.name == role }
            ?: throw IllegalArgumentException("unknown child role: $role")
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
