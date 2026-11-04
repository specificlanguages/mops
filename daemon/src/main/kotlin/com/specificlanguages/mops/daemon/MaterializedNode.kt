package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode

/** A node that an Edit Operation materialized, and whether it was created fresh or as part of a copied subtree. */
internal data class MaterializedNode(val node: SNode, val origin: Origin) {
    enum class Origin { FRESH, COPY }
}
