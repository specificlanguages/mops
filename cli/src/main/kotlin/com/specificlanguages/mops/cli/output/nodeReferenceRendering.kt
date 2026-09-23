package com.specificlanguages.mops.cli.output

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val MPS_NODE_URL = "http://127.0.0.1:63320/node?ref="
private const val ESC = '\u001B'

internal fun renderNodeReference(
    reference: String,
    format: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
    hyperlinks: Boolean = System.console() != null,
): String {
    if (format == NodeReferenceFormat.SERIALIZED && !hyperlinks) return reference

    val url = nodeReferenceUrl(reference)
    if (format == NodeReferenceFormat.URL) return url
    return "$ESC]8;;$url$ESC\\$reference$ESC]8;;$ESC\\"
}

private fun nodeReferenceUrl(reference: String): String {
    val encodedReference = URLEncoder.encode(reference, StandardCharsets.UTF_8).replace("+", "%20")
    return "$MPS_NODE_URL$encodedReference"
}
