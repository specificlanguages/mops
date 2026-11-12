package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.output.displayConcept
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.cli.output.TextColumn
import com.specificlanguages.mops.cli.output.nodeReferenceColumn
import com.specificlanguages.mops.cli.output.terminalHyperlinks
import com.specificlanguages.mops.cli.output.textRow
import com.specificlanguages.mops.protocol.MpsNodeParentJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeUsageJson

internal fun renderText(
    node: MpsNodeSummaryJson,
    fullConcept: Boolean,
    nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
    hyperlinks: Boolean = terminalHyperlinks(),
): String =
    textRow(
        listOf(
            TextColumn(node.type),
            TextColumn(node.name ?: "<unnamed>"),
            TextColumn(displayConcept(node.concept, fullConcept)),
            nodeReferenceColumn(node.reference, nodeReferenceFormat, hyperlinks),
        ) + parentColumns(node.parent, fullConcept, nodeReferenceFormat, hyperlinks),
    )

internal fun renderText(
    usage: MpsNodeUsageJson,
    fullConcept: Boolean,
    nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
    hyperlinks: Boolean = terminalHyperlinks(),
): String {
    val owner = usage.owner
    return textRow(
        listOf(
            TextColumn("usage"),
            TextColumn(usage.role),
            TextColumn(owner.name ?: "<unnamed>"),
            TextColumn(displayConcept(owner.concept, fullConcept)),
            nodeReferenceColumn(owner.reference, nodeReferenceFormat, hyperlinks),
        ) + parentColumns(owner.parent, fullConcept, nodeReferenceFormat, hyperlinks),
    )
}

/**
 * Trailing tab columns describing a result node's immediate parent: its name (or `<unnamed>`), concept, and node
 * reference. The concept is shown as a short name unless [fullConcept] is set. Empty when the node is a Root Node, so
 * root results keep their existing shorter rows.
 */
private fun parentColumns(
    parent: MpsNodeParentJson?,
    fullConcept: Boolean,
    nodeReferenceFormat: NodeReferenceFormat,
    hyperlinks: Boolean,
): List<TextColumn> =
    if (parent == null) {
        emptyList()
    } else {
        listOf(
            TextColumn("parent"),
            TextColumn(parent.name ?: "<unnamed>"),
            TextColumn(displayConcept(parent.concept, fullConcept)),
            nodeReferenceColumn(parent.reference, nodeReferenceFormat, hyperlinks),
        )
    }
