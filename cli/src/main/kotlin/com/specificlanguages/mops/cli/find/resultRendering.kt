package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.output.displayConcept
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.protocol.MpsNodeParentJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeUsageJson

internal fun renderText(
    node: MpsNodeSummaryJson,
    fullConcept: Boolean,
    nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
): String =
    (
        listOf(
            node.type,
            node.name ?: "<unnamed>",
            displayConcept(node.concept, fullConcept),
            renderNodeReference(node.reference, nodeReferenceFormat),
        ) + parentColumns(node.parent, fullConcept, nodeReferenceFormat)
    ).joinToString("\t")

internal fun renderText(
    usage: MpsNodeUsageJson,
    fullConcept: Boolean,
    nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
): String {
    val owner = usage.owner
    return (
        listOf(
            "usage",
            usage.role,
            owner.name ?: "<unnamed>",
            displayConcept(owner.concept, fullConcept),
            renderNodeReference(owner.reference, nodeReferenceFormat),
        ) + parentColumns(owner.parent, fullConcept, nodeReferenceFormat)
    ).joinToString("\t")
}

/**
 * Trailing tab columns describing a result node's immediate parent: its name (or `<unnamed>`), concept, and node
 * reference. The concept is shown as a short name unless [fullConcept] is set. Empty when the node is a Root Node, so
 * root results keep their existing shorter rows.
 */
internal fun parentColumns(
    parent: MpsNodeParentJson?,
    fullConcept: Boolean,
    nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
): List<String> =
    if (parent == null) {
        emptyList()
    } else {
        listOf(
            "parent",
            parent.name ?: "<unnamed>",
            displayConcept(parent.concept, fullConcept),
            renderNodeReference(parent.reference, nodeReferenceFormat),
        )
    }
