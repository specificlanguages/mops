package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.MpsNodeParentJson

/**
 * Trailing tab columns describing a result node's immediate parent: its name (or `<unnamed>`), concept, and serialized
 * node reference. Empty when the node is a Root Node, so root results keep their existing shorter rows.
 */
internal fun parentColumns(parent: MpsNodeParentJson?): List<String> =
    if (parent == null) {
        emptyList()
    } else {
        listOf("parent", parent.name ?: "<unnamed>", parent.concept, parent.reference)
    }
