package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MpsNodeJson

fun propertyValue(node: MpsNodeJson, name: String): String =
    requireNotNull(propertyValueOrNull(node, name)) { "missing property $name in $node" }

fun propertyValueOrNull(node: MpsNodeJson, name: String): String? =
    node.properties?.singleOrNull { it.name == name }?.value
