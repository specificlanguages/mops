package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Parses [text] as a serialized **Model Reference** when its model ID carries a type designator (`r:`, `f:`, `path:`,
 * `java:` and so on), optionally after a `moduleId/` prefix and before a `(model name)` part. Returns null for any other
 * text, and for text MPS cannot parse.
 *
 * MPS's `createModelReference` reads text without a type designator as a name-based model ID. mops does not support
 * name-based model IDs, so such text never reaches the MPS parser here and callers read it as a model name instead.
 */
internal fun parseModelReferenceOrNull(persistence: PersistenceFacade, text: String): SModelReference? {
    val modelId = text.trim().substringBefore('(').substringAfter('/')
    if (':' !in modelId) return null
    return runCatching { persistence.createModelReference(text) }.getOrNull()
}
