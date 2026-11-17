package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.model.SReference

/**
 * Live indexed access to native references by role name or by [SReferenceLink] descriptor, including inherited links. Iterates all set references like
 * native `SNode.getReferences()`, which it shadows in Groovy.
 */
class NodeReferences(private val node: SNode) : NodeAccessor<SReference>("references", "role", "SNodeOperations.getReferences(node)") {
    override fun iterator(): Iterator<SReference> {
        requireRead("SNode.references")
        return node.references.iterator()
    }

    fun getAt(role: String): SReference? {
        requireRead("SNode.references[]")
        return node.getReference(node.requireReferenceLink(role))
    }

    fun getAt(link: SReferenceLink): SReference? {
        requireRead("SNode.references[]")
        return node.getReference(node.requireReferenceLink(link))
    }

    /** Resolves and assigns a target through MPS reference setters, or clears it with null. */
    fun putAt(role: String, value: Any?) {
        requireCommand("SNode.references[] assignment")
        write(node.requireReferenceLink(role), value)
    }

    /** Resolves and assigns a target through MPS reference setters, or clears it with null. */
    fun putAt(link: SReferenceLink, value: Any?) {
        requireCommand("SNode.references[] assignment")
        write(node.requireReferenceLink(link), value)
    }

    private fun write(link: SReferenceLink, value: Any?) {
        val role = link.name
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
