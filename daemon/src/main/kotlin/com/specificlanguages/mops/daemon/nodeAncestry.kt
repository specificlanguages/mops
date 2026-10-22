package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsNodeParentJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import jetbrains.mps.smodel.SNodeUtil
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Builds the summary vocabulary `find`, `check model`, and `get node --summary` share for one node: whether it is a
 * Root Node, its name, **MPS Concept** qualified name, concept validity, serialized Node Reference, and its parent
 * (the immediate one, or the chain up to the Root Node when [fullChain] is set).
 */
internal fun nodeSummary(node: SNode, persistence: PersistenceFacade, fullChain: Boolean = false): MpsNodeSummaryJson =
    MpsNodeSummaryJson(
        type = if (node.parent == null) "root" else "node",
        name = nodeName(node),
        concept = node.concept.qualifiedName,
        conceptValid = node.concept.isValid,
        reference = persistence.asString(node.reference),
        parent = nodeParent(node, fullChain, persistence),
    )

/**
 * Builds the containment chain above [node]: the immediate parent, and, when [fullChain] is set, that parent's own
 * parent recursively up to the Root Node. Returns null when [node] is a Root Node.
 *
 * Each entry's `role` is the containment role by which the node one level below it (the node whose parent it is) sits in
 * it, so `node.parent.role` reads as "the role this node occupies in its parent".
 */
internal fun nodeParent(node: SNode, fullChain: Boolean, persistence: PersistenceFacade): MpsNodeParentJson? {
    val parent = node.parent ?: return null
    return MpsNodeParentJson(
        type = if (parent.parent == null) "root" else "node",
        role = node.containmentLink?.role,
        name = nodeName(parent),
        concept = parent.concept.qualifiedName,
        conceptValid = parent.concept.isValid,
        reference = persistence.asString(parent.reference),
        parent = if (fullChain) nodeParent(parent, fullChain = true, persistence) else null,
    )
}

/** The Node Name as MPS computes it, including property getters; null for a node without a name. */
internal fun nodeName(node: SNode): String? =
    SNodeAccessUtil.getPropertyValue(node, SNodeUtil.property_INamedConcept_name) as String?
