package com.specificlanguages.mops.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One position in an Inline Subtree's `children` array. Every position carries the Containment [role] it fills under
 * its parent, and is exactly one of: a fresh-node spec ([Fresh]), a Move Leaf that adopts an existing node
 * identity-preservingly ([Move]), or a Copy Leaf that deep-copies an existing node with fresh ids ([Copy]).
 */
@Serializable(with = InlineChildSerializer::class)
sealed interface InlineChild {
    val role: String?

    /**
     * A fresh node of [concept] carrying inline [properties], [references] to existing nodes, and a nested [children]
     * subtree. Shaped like the tree `get-node` emits, so output (which also carries `model`/`id`/`parent`) decodes here
     * with those enrichment fields ignored.
     */
    @Serializable
    @SerialName("InlineNodeSpec")
    data class Fresh(
        override val role: String? = null,
        val concept: String,
        val properties: List<MpsNodePropertyJson>? = null,
        val references: List<InlineReference>? = null,
        val children: List<InlineChild>? = null,
    ) : InlineChild

    data class Move(override val role: String?, val source: EditTarget) : InlineChild
    data class Copy(override val role: String?, val source: EditTarget) : InlineChild
}

/**
 * An inline Reference set on a freshly built node, pointing at an existing node under [role]. The target is given
 * either as [to] — the canonical `EditTarget` grammar `setReference` uses (a node reference string, a `$`-alias, or
 * a `{model, nodeId}` object) — or as [target], the get-node-shaped `{model, node, ...}` object, so `get-node`
 * output round-trips (its enrichment fields such as `name`/`concept`/`resolved` are ignored on input). Exactly one
 * of [to] and [target] is present.
 */
@Serializable(with = InlineReferenceSerializer::class)
data class InlineReference(
    val role: String,
    val to: EditTarget? = null,
    val target: MpsNodeReferenceTargetJson? = null,
)

internal object InlineChildSerializer : KSerializer<InlineChild> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InlineChild")

    override fun serialize(encoder: Encoder, value: InlineChild) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw UnsupportedJsonOnlySerializerException("InlineChild")
        val element = when (value) {
            is InlineChild.Fresh -> jsonEncoder.json.encodeToJsonElement(InlineChild.Fresh.serializer(), value)
            is InlineChild.Move -> leafObject(jsonEncoder, "move", value.role, value.source)
            is InlineChild.Copy -> leafObject(jsonEncoder, "copy", value.role, value.source)
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): InlineChild {
        val jsonDecoder = decoder as? JsonDecoder ?: throw UnsupportedJsonOnlySerializerException("InlineChild")
        val obj = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: throw InlineSubtreeException("has an inline child that is not a JSON object")

        val hasMove = "move" in obj
        val hasCopy = "copy" in obj
        if (hasMove && hasCopy) {
            throw InlineSubtreeException("has a child that sets both \"move\" and \"copy\"; a leaf is one or the other")
        }
        if (hasMove || hasCopy) {
            val leaf = if (hasMove) "move" else "copy"
            val extras = obj.keys.filter { it != "role" && it != leaf }
            if (extras.isNotEmpty()) {
                throw InlineSubtreeException(
                    "has a child that mixes a \"$leaf\" leaf with fresh-node field(s) ${extras.joinToString()}; " +
                        "a leaf carries only \"role\" and \"$leaf\"",
                )
            }
            val source = jsonDecoder.json.decodeFromJsonElement(EditTarget.serializer(), obj.getValue(leaf))
            val role = obj.stringField("role")
            return if (hasMove) InlineChild.Move(role, source) else InlineChild.Copy(role, source)
        }
        return jsonDecoder.json.decodeFromJsonElement(InlineChild.Fresh.serializer(), obj)
    }

    private fun leafObject(encoder: JsonEncoder, key: String, role: String?, source: EditTarget): JsonObject =
        buildJsonObject {
            if (role != null) put("role", role)
            put(key, encoder.json.encodeToJsonElement(EditTarget.serializer(), source))
        }
}

internal object InlineReferenceSerializer : KSerializer<InlineReference> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InlineReference")

    override fun serialize(encoder: Encoder, value: InlineReference) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw UnsupportedJsonOnlySerializerException("InlineReference")
        val element = buildJsonObject {
            put("role", value.role)
            value.to?.let { put("to", jsonEncoder.json.encodeToJsonElement(EditTarget.serializer(), it)) }
            value.target?.let {
                put("target", jsonEncoder.json.encodeToJsonElement(MpsNodeReferenceTargetJson.serializer(), it))
            }
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): InlineReference {
        val jsonDecoder = decoder as? JsonDecoder ?: throw UnsupportedJsonOnlySerializerException("InlineReference")
        val obj = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: throw InlineSubtreeException("has an inline reference that is not a JSON object")

        val role = obj.stringField("role") ?: throw InlineSubtreeException("has an inline reference missing its \"role\"")
        val hasTo = obj["to"].let { it != null && it !is JsonNull }
        val hasTarget = obj["target"].let { it != null && it !is JsonNull }
        if (hasTo && hasTarget) {
            throw InlineSubtreeException("has a reference \"$role\" that sets both \"to\" and \"target\"; use one")
        }
        if (!hasTo && !hasTarget) {
            throw InlineSubtreeException("has a reference \"$role\" with neither \"to\" nor \"target\"")
        }
        val to = if (hasTo) jsonDecoder.json.decodeFromJsonElement(EditTarget.serializer(), obj.getValue("to")) else null
        val target = if (hasTarget) {
            jsonDecoder.json.decodeFromJsonElement(MpsNodeReferenceTargetJson.serializer(), obj.getValue("target"))
        } else {
            null
        }
        return InlineReference(role = role, to = to, target = target)
    }
}
