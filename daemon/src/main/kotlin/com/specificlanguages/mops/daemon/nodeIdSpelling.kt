package com.specificlanguages.mops.daemon

import jetbrains.mps.smodel.persistence.def.v9.IdEncoder
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Parses a **Node ID** given in either spelling mops accepts: the decimal form mops prints, or the encoded
 * `IdEncoder` form persisted in `.mps` files. Returns null when [nodeId] parses as neither.
 *
 * An all-digit string is read as the decimal form; anything else is read as the encoded form. The encoded
 * base64 alphabet also spans the digits, so an all-digit string is ambiguous between the two — decimal wins
 * because that is the only all-digit form mops itself prints.
 */
internal fun parseNodeIdOrNull(persistence: PersistenceFacade, nodeId: String): SNodeId? =
    runCatching {
        if (nodeId.all(Char::isDigit)) persistence.createNodeId(nodeId) else IdEncoder().parseNodeId(nodeId)
    }.getOrNull()

/**
 * Rewrites a serialized **Node Reference** so its id part resolves whichever spelling it uses. MPS's
 * `createNodeReference` reads only the decimal id form, so an id in the encoded persisted spelling is
 * normalized to decimal here before the reference is handed on. A reference with no id part, or whose id
 * part parses as neither spelling, is returned unchanged, leaving the caller to handle it as it would any
 * reference it cannot resolve.
 */
internal fun normalizeNodeReferenceSpelling(persistence: PersistenceFacade, reference: String): String {
    val slash = reference.lastIndexOf('/')
    if (slash < 0) return reference
    val nodeId = parseNodeIdOrNull(persistence, reference.substring(slash + 1)) ?: return reference
    return reference.substring(0, slash + 1) + persistence.asString(nodeId)
}
