package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.model.SReference

/** Live indexed access to native references by role, including inherited links. */
class NodeReferences(private val node: SNode) {
    fun getAt(role: String): SReference? {
        requireRead("SNode.references[]")
        val link = node.concept.referenceLinks.firstOrNull { it.name == role } ?: return null
        return node.getReference(link)
    }

    /** Assigns a target, copies a reference's target description, or clears the reference with null. */
    fun putAt(role: String, value: Any?) {
        requireCommand("SNode.references[] assignment")
        val link = node.concept.referenceLinks.firstOrNull { it.name == role }
            ?: throw IllegalArgumentException("unknown reference role: $role")
        when (value) {
            null -> node.dropReference(link)
            is SNode -> node.setReferenceTarget(link, value)
            is SNodeReference -> node.setReference(link, value)
            is SReference -> node.setReference(link, value.describeTarget())
            else -> throw IllegalArgumentException("reference must be a node, node reference, SReference, or null")
        }
    }
}
