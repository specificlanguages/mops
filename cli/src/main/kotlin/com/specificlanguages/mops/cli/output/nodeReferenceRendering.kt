package com.specificlanguages.mops.cli.output

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val MPS_NODE_URL = "http://127.0.0.1:63320/node?ref="

/** Whether text output links node references to MPS: on when mops writes to a terminal. */
internal fun terminalHyperlinks(): Boolean = System.console() != null

/**
 * A node [reference] as a text column: under [NodeReferenceFormat.URL], the MPS node URL; otherwise the serialized
 * reference, linked to that URL when [hyperlinks] is on.
 */
internal fun nodeReferenceColumn(
    reference: String,
    format: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
    hyperlinks: Boolean = terminalHyperlinks(),
): TextColumn =
    when {
        format == NodeReferenceFormat.URL -> TextColumn(nodeReferenceUrl(reference))
        hyperlinks -> TextColumn(reference, link = nodeReferenceUrl(reference))
        else -> TextColumn(reference)
    }

/** A node [reference] as text outside a [textRow]: [nodeReferenceColumn] rendered without escaping. */
internal fun renderNodeReference(
    reference: String,
    format: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
    hyperlinks: Boolean = terminalHyperlinks(),
): String = nodeReferenceColumn(reference, format, hyperlinks).render()

private fun nodeReferenceUrl(reference: String): String {
    val encodedReference = URLEncoder.encode(reference, StandardCharsets.UTF_8).replace("+", "%20")
    return "$MPS_NODE_URL$encodedReference"
}
