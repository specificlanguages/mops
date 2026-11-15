/**
 * Adapter. Points a node's reference at a target node, or clears it, the way the MPS editor and smodel code do. Code
 * Mode reference assignments and edit batches both write references here.
 *
 * Hides: that a reference write goes through [SNodeAccessUtil.setReferenceTarget], which runs the concept's referent
 * set handler: `validate`, then `onReferenceSet`. The handler may change other parts of the node, and a `validate` that
 * returns false leaves the reference unchanged without an error. See
 * https://github.com/specificlanguages/mps-api-research/blob/8421ecf/docs/node-feature-setter-hooks.md.
 */
package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil

/** Sets [link] of [node] to [target], or clears it when [target] is null, through MPS reference setter hooks. */
internal fun writeReferenceTarget(node: SNode, link: SReferenceLink, target: SNode?) {
    SNodeAccessUtil.setReferenceTarget(node, link, target)
}
