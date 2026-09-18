package com.specificlanguages.mops.cli.output

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val MPS_NODE_URL = "http://127.0.0.1:63320/node?ref="
private const val ESC = '\u001B'

internal fun renderNodeReference(
    reference: String,
    hyperlinks: Boolean = System.console() != null,
): String {
    if (!hyperlinks) return reference

    val encodedReference = URLEncoder.encode(reference, StandardCharsets.UTF_8).replace("+", "%20")
    return "$ESC]8;;$MPS_NODE_URL$encodedReference$ESC\\$reference$ESC]8;;$ESC\\"
}
