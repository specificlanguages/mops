/**
 * Logic. Decides which notation a **Node Target** written as one string uses.
 *
 * Hides: how the text of a one-string node target maps to a node reference or a model name with a node ID. Commands,
 * Code Mode lookups, edit batches and navigation target segments parse a one-string node target only through here.
 */
package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.NODE_TARGET_NOTATIONS
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/** A **Node Target** written as one string, parsed into the notation it uses. */
internal sealed interface ParsedNodeTarget {
    /** A serialized **Node Reference**. It names its model by model ID, so a stale name part does not matter. */
    data class Reference(val reference: SNodeReference) : ParsedNodeTarget

    /** A **Model Name** and a **Node ID**. Several models may share the name. */
    data class InNamedModel(val modelName: String, val nodeId: SNodeId) : ParsedNodeTarget
}

/**
 * Parses [text], split at its last `/` into a model part and a node ID part. See [parseNodeTarget] with both parts.
 * Throws [IllegalArgumentException] that lists [NODE_TARGET_NOTATIONS] when [text] uses neither notation.
 */
internal fun parseNodeTarget(persistence: PersistenceFacade, text: String): ParsedNodeTarget {
    val slash = text.lastIndexOf('/')
    if (slash <= 0) throw invalidNodeTarget(text)
    return parseNodeTarget(persistence, text.substring(0, slash), text.substring(slash + 1))
}

/**
 * Parses a node target given as a model part and a node ID part. When [model] parses as a serialized model reference,
 * the target is a serialized node reference. Any other [model] is a model name. The node ID may use its decimal or
 * encoded spelling. Throws [IllegalArgumentException] that lists [NODE_TARGET_NOTATIONS] when the parts use neither
 * notation.
 */
internal fun parseNodeTarget(persistence: PersistenceFacade, model: String, nodeId: String): ParsedNodeTarget {
    val text = "$model/$nodeId"
    if (parseModelReferenceOrNull(persistence, model) != null) {
        val reference = try {
            persistence.createNodeReference(normalizeNodeReferenceSpelling(persistence, text))
        } catch (failure: IllegalArgumentException) {
            throw invalidNodeTarget(text, failure)
        }
        return ParsedNodeTarget.Reference(reference)
    }
    val id = parseNodeIdOrNull(persistence, nodeId) ?: throw invalidNodeTarget(text)
    return ParsedNodeTarget.InNamedModel(model, id)
}

private fun invalidNodeTarget(text: String, cause: Throwable? = null) =
    IllegalArgumentException("invalid node target: $text; accepted notations: $NODE_TARGET_NOTATIONS", cause)
