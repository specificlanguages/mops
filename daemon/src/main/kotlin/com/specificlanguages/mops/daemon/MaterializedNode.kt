package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SNode

/** A node that an Edit Operation materialized: created fresh, or ([copied]) as part of a copied subtree. */
internal data class MaterializedNode(val node: SNode, val copied: Boolean)
