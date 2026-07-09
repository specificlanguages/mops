package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Two-pass, notation-level decoder for an edit batch: parse the text to a [JsonElement], then validate and decode each
 * `operations[i]` one element at a time. Every problem is attributed to an operation index and (once the `op`
 * discriminator is read) an op kind by inspecting the JSON tree structurally — never by parsing serializer exception
 * strings — so each [BatchDecodeResult.Failure] can name what is wrong, where, and which `mops explain` page to read.
 *
 * The supported-op list and the required/allowed field sets come from [EditNotation], so a new operation or a changed
 * field flows into the messages without editing this decoder.
 */
internal object BatchNotationDecoder {
    // The custom-serializer types whose fields need shared-type explain pointers (target vs position).
    private const val EDIT_TARGET = "EditTarget"
    private const val CHILD_POSITION = "ChildPosition"

    // Nearest-suggestion cutoff for the tier-1 "Did you mean" clause, measured on the prefix-credited edit distance.
    private const val SUGGESTION_CUTOFF = 4

    fun decode(json: Json, text: String): BatchDecodeResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return batchShape()
        }
        if (root !is JsonObject) return batchShape()
        val operations = root["operations"] as? JsonArray ?: return batchShape()

        operations.forEachIndexed { index, element ->
            validateOperation(json, index, element)?.let { return it }
        }
        return BatchDecodeResult.Success(json.decodeFromJsonElement(EditBatch.serializer(), root))
    }

    private fun validateOperation(json: Json, index: Int, element: JsonElement): BatchDecodeResult.Failure? {
        if (element !is JsonObject) {
            return failure(index, null, BatchDecodeErrorCategory.WrongType,
                "operations[$index]: operation must be a JSON object — see: mops explain edit")
        }

        val op = (element["op"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (op == null || op !in EditNotation.operationNames) {
            return unknownOp(index, op)
        }

        val allowed = EditNotation.serializedFieldNames(op).toSet() + "op"
        element.keys.firstOrNull { it !in allowed }?.let { unknown ->
            return failure(index, op, BatchDecodeErrorCategory.UnknownField,
                """operations[$index]: $op has unknown field "$unknown" — see: mops explain edit.$op""")
        }

        EditNotation.requiredFieldNames(op).firstOrNull { it !in element.keys }?.let { missing ->
            return failure(index, op, BatchDecodeErrorCategory.MissingField,
                """operations[$index]: $op requires "$missing" — see: mops explain edit.$op""")
        }

        for ((field, value) in element) {
            if (field == "op") continue
            val rawType = EditNotation.fieldTypeName(op, field) ?: continue
            val nullable = rawType.endsWith("?")
            if (value is JsonNull && nullable) continue
            when (rawType.removeSuffix("?")) {
                EDIT_TARGET -> if (!decodes(json, EditTargetSerializer, value)) {
                    return failure(index, op, BatchDecodeErrorCategory.InvalidTarget,
                        """operations[$index]: $op field "$field" is not a valid target — see: mops explain target""")
                }
                CHILD_POSITION -> if (!decodes(json, ChildPositionSerializer, value)) {
                    return failure(index, op, BatchDecodeErrorCategory.InvalidPosition,
                        """operations[$index]: $op field "$field" is not a valid position — see: mops explain position""")
                }
            }
        }

        // Catch-all for remaining shape problems the structural checks above do not model (for example a primitive
        // field given the wrong JSON type), attributed to this op's page. Inline-subtree serializers (children,
        // references) raise a [ProtocolJsonException] whose message already names the offending field, so surface it
        // verbatim rather than the generic fallback.
        return try {
            json.decodeFromJsonElement(EditOperation.serializer(), element)
            null
        } catch (exception: ProtocolJsonException) {
            failure(index, op, BatchDecodeErrorCategory.WrongType,
                "operations[$index]: $op ${exception.message} — see: mops explain edit.$op")
        } catch (_: SerializationException) {
            failure(index, op, BatchDecodeErrorCategory.WrongType,
                "operations[$index]: $op has an invalid field — see: mops explain edit.$op")
        }
    }

    private fun <T> decodes(json: Json, serializer: kotlinx.serialization.KSerializer<T>, value: JsonElement): Boolean =
        runCatching { json.decodeFromJsonElement(serializer, value) }.isSuccess

    private fun batchShape(): BatchDecodeResult.Failure =
        failure(null, null, BatchDecodeErrorCategory.BatchShape,
            """edit batch must be a JSON object with an "operations" array — see: mops explain edit""")

    private fun unknownOp(index: Int, op: String?): BatchDecodeResult.Failure {
        val supported = EditNotation.operationNames.joinToString(", ")
        val lead = if (op == null) """missing "op"""" else """unknown op "$op""""
        val suggestion = op?.let { suggestFor(it) }
        val didYouMean = suggestion?.let { """Did you mean "$it"? """ }.orEmpty()
        return failure(index, op, BatchDecodeErrorCategory.UnknownOp,
            "operations[$index]: $lead — supported: $supported. ${didYouMean}See: mops explain edit")
    }

    // Nearest supported op by edit distance, crediting a shared prefix so `addNode` prefers `addChild` over an
    // equidistant alternative; omitted when nothing is close enough to be a plausible typo.
    private fun suggestFor(op: String): String? {
        val best = EditNotation.operationNames.minByOrNull { levenshtein(op, it) - commonPrefixLength(op, it) }
            ?: return null
        val score = levenshtein(op, best) - commonPrefixLength(op, best)
        return if (score <= SUGGESTION_CUTOFF) best else null
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var i = 0
        while (i < limit && a[i] == b[i]) i++
        return i
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            for (k in prev.indices) prev[k] = curr[k]
        }
        return prev[b.length]
    }

    private fun failure(
        operationIndex: Int?,
        opKind: String?,
        category: BatchDecodeErrorCategory,
        detail: String,
    ): BatchDecodeResult.Failure = BatchDecodeResult.Failure(operationIndex, opKind, category, detail)
}
