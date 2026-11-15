/**
 * Adapter. The one reference write that Code Mode reference assignments and edit batches share, so that every path
 * that sets a reference runs the same MPS reference setter hooks as the MPS editor.
 *
 * Hides: which MPS API sets a reference ([SNodeAccessUtil.setReferenceTarget], not the raw `SNode.setReferenceTarget`).
 * What the hooks do: https://github.com/specificlanguages/mps-api-research/blob/8421ecf/docs/node-feature-setter-hooks.md.
 */
package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil

/** Sets [link] of [node] to [target], or clears it when [target] is null, through MPS reference setter hooks. */
internal fun writeReferenceTarget(node: SNode, link: SReferenceLink, target: SNode?) {
    SNodeAccessUtil.setReferenceTarget(node, link, target)
}
