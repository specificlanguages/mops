package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.model.SReference

/** Live indexed access to native references by role, including inherited links. */
class NodeReferences(private val node: SNode) {
    fun getAt(role: String): SReference? {
        requireRead("SNode.references[]")
        val link = node.concept.referenceLinks.firstOrNull { it.name == role } ?: return null
        return node.getReference(link)
    }

    /** Resolves and assigns a target through MPS reference setters, or clears it with null. */
    fun putAt(role: String, value: Any?) {
        requireCommand("SNode.references[] assignment")
        val link = node.concept.referenceLinks.firstOrNull { it.name == role }
            ?: throw IllegalArgumentException("unknown reference role: $role")
        val target = when (value) {
            null -> null
            is SNode -> value
            is SNodeReference -> value.resolve(CodeModeProjectProvider.project().repository)
                ?: throw IllegalArgumentException("unresolved reference target for role: $role")
            is SReference -> value.targetNode
                ?: throw IllegalArgumentException("unresolved reference target for role: $role")
            else -> throw IllegalArgumentException("reference must be a node, node reference, SReference, or null")
        }
        SNodeAccessUtil.setReferenceTarget(node, link, target)
    }
}
