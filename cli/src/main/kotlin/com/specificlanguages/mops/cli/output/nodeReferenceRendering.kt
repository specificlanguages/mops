package com.specificlanguages.mops.cli.output

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val MPS_NODE_URL = "http://127.0.0.1:63320/node?ref="

/** A node [reference] as a text column in [format]. */
internal fun nodeReferenceColumn(reference: String, format: NodeReferenceFormat): TextColumn =
    when (format) {
        NodeReferenceFormat.SERIALIZED -> TextColumn(reference)
        NodeReferenceFormat.LINKED -> TextColumn(reference, link = nodeReferenceUrl(reference))
        NodeReferenceFormat.URL -> TextColumn(nodeReferenceUrl(reference))
    }

/** A node [reference] as text outside a [textRow]: [nodeReferenceColumn] rendered without escaping. */
internal fun renderNodeReference(reference: String, format: NodeReferenceFormat): String =
    nodeReferenceColumn(reference, format).render()

private fun nodeReferenceUrl(reference: String): String {
    val encodedReference = URLEncoder.encode(reference, StandardCharsets.UTF_8).replace("+", "%20")
    return "$MPS_NODE_URL$encodedReference"
}
