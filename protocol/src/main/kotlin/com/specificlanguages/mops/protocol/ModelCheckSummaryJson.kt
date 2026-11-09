package com.specificlanguages.mops.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

/**
 * The final record of a `check --format jsonl` stream: how many findings were printed before it ([shown]) and the
 * check's [summary]. Findings have no `type` field, so a reader tells this record apart by its `type` of `summary`.
 *
 * The record is flat. After `type`, `shown`, and `total` it carries every field of [ModelCheckSummary] in declaration
 * order, so a field added there appears here too. [hint] names the option that lifts the cap and is present only when
 * the list was truncated.
 */
@Serializable(with = ModelCheckSummaryJsonSerializer::class)
data class ModelCheckSummaryJson(
    val shown: Int,
    val summary: ModelCheckSummary,
    val hint: String? = null,
)

internal object ModelCheckSummaryJsonSerializer : KSerializer<ModelCheckSummaryJson> {
    private const val TYPE = "type"
    private const val SHOWN = "shown"
    private const val TOTAL = "total"
    private const val HINT = "hint"

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ModelCheckSummaryJson")

    override fun serialize(encoder: Encoder, value: ModelCheckSummaryJson) {
        val jsonEncoder = encoder as JsonEncoder
        val summary = jsonEncoder.json.encodeToJsonElement(serializer<ModelCheckSummary>(), value.summary).jsonObject
        val record = buildJsonObject {
            put(TYPE, "summary")
            put(SHOWN, value.shown)
            put(TOTAL, value.summary.total)
            summary.forEach { (key, element) -> put(key, element) }
            value.hint?.let { put(HINT, it) }
        }
        jsonEncoder.encodeJsonElement(record)
    }

    override fun deserialize(decoder: Decoder): ModelCheckSummaryJson {
        val jsonDecoder = decoder as JsonDecoder
        val record = jsonDecoder.decodeJsonElement().jsonObject
        val summary = JsonObject(record - setOf(TYPE, SHOWN, TOTAL, HINT))
        return ModelCheckSummaryJson(
            shown = record.getValue(SHOWN).jsonPrimitive.int,
            summary = jsonDecoder.json.decodeFromJsonElement(serializer<ModelCheckSummary>(), summary),
            hint = record[HINT]?.jsonPrimitive?.content,
        )
    }
}
