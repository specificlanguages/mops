package com.specificlanguages.mops.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Encodes and decodes one protocol type with the shared [ProtocolJson] settings.
 *
 * The public methods are non-inline and expose no kotlinx.serialization type, for the same reason as the methods of
 * [ProtocolJson]: consumers compile against the thin protocol jar but run against the relocated one.
 */
class JsonCodec<T> internal constructor(
    private val json: Json,
    private val prettyJson: Json,
    private val serializer: KSerializer<T>,
) {
    /**
     * Encodes [value] as compact JSON, or as indented JSON with one field per line when [pretty] is set.
     */
    fun encode(value: T, pretty: Boolean = false): String =
        (if (pretty) prettyJson else json).encodeToString(serializer, value)

    fun decode(text: String): T = json.decodeFromString(serializer, text)
}
