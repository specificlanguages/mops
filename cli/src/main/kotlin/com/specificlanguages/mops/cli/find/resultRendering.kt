package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.output.displayConcept
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.protocol.MpsNodeParentJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeUsageJson

internal fun renderText(node: MpsNodeSummaryJson, fullConcept: Boolean): String =
    (
        listOf(
            node.type,
            node.name ?: "<unnamed>",
            displayConcept(node.concept, fullConcept),
            renderNodeReference(node.reference),
        ) + parentColumns(node.parent, fullConcept)
    ).joinToString("\t")

internal fun renderText(usage: MpsNodeUsageJson, fullConcept: Boolean): String {
    val owner = usage.owner
    return (
        listOf(
            "usage",
            usage.role,
            owner.name ?: "<unnamed>",
            displayConcept(owner.concept, fullConcept),
            renderNodeReference(owner.reference),
        ) + parentColumns(owner.parent, fullConcept)
    ).joinToString("\t")
}

/**
 * Trailing tab columns describing a result node's immediate parent: its name (or `<unnamed>`), concept, and serialized
 * node reference. The concept is shown as a short name unless [fullConcept] is set. Empty when the node is a Root Node,
 * so root results keep their existing shorter rows.
 */
internal fun parentColumns(parent: MpsNodeParentJson?, fullConcept: Boolean): List<String> =
    if (parent == null) {
        emptyList()
    } else {
        listOf(
            "parent",
            parent.name ?: "<unnamed>",
            displayConcept(parent.concept, fullConcept),
            renderNodeReference(parent.reference),
        )
    }
