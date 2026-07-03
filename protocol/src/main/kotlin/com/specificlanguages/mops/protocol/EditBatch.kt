package com.specificlanguages.mops.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Ordered batch of Edit Operations applied atomically by `mops model edit`.
 */
@Serializable
data class EditBatch(
    val operations: List<EditOperation>,
)

/**
 * One edit operation. The wire `op` discriminator is derived from each leaf's [SerialName].
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("op")
sealed interface EditOperation {

    val target: EditTarget

    @Serializable
    @SerialName("setProperty")
    data class SetProperty(
        override val target: EditTarget,
        val name: String,
        val value: String? = null,
    ) : EditOperation

    @Serializable
    @SerialName("delete")
    data class Delete(override val target: EditTarget) : EditOperation

    @Serializable
    @SerialName("deleteChild")
    data class DeleteChild(
        override val target: EditTarget,
        val role: String,
        val position: ChildPosition = ChildPosition.Only
    ) : EditOperation

    /**
     * Adds a child of [concept] under the [target] parent's containment [role]. The new node may carry inline
     * [properties], [references] to existing nodes, and a nested [children] subtree in the same shape `get-node` emits.
     * [position] is the insertion point among the role's existing children.
     */
    @Serializable
    @SerialName("addChild")
    data class AddChild(
        override val target: EditTarget,
        val role: String,
        val concept: String,
        val properties: List<MpsNodePropertyJson>? = null,
        val references: List<MpsNodeReferenceJson>? = null,
        val children: List<MpsNodeJson>? = null,
        val position: ChildPosition = ChildPosition.Last,
    ) : EditOperation

    /**
     * Moves the [target] node under the [into] parent's containment [role] at [position], detaching it from its
     * current location.
     */
    @Serializable
    @SerialName("moveNode")
    data class MoveNode(
        override val target: EditTarget,
        val into: EditTarget,
        val role: String,
        val position: ChildPosition = ChildPosition.Last,
    ) : EditOperation
}

/**
 * Position of a child among the existing children in a containment role. [First], [Last], and [Index] address an
 * insertion point or an existing child by order; [Only] addresses the single child in a single-valued role.
 */
@Serializable(with = ChildPositionSerializer::class)
sealed interface ChildPosition {
    object First : ChildPosition
    object Last : ChildPosition
    object Only : ChildPosition

    data class Index(val index: Int) : ChildPosition
}

/**
 * Target of an edit operation. Encoded either as a bare reference string (a `$`-prefixed alias or a node reference) or
 * as a `{model, nodeId}` object; decoding mirrors that shape.
 */
@Serializable(with = EditTargetSerializer::class)
sealed interface EditTarget {
    data class NodeReference(val nodeReference: String) : EditTarget
    data class InModel(val modelTarget: String, val nodeId: String) : EditTarget
    data class Alias(val alias: String) : EditTarget
}

internal object EditTargetSerializer : KSerializer<EditTarget> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EditTarget")

    override fun serialize(encoder: Encoder, value: EditTarget) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw UnsupportedJsonOnlySerializerException("EditTarget")
        val element = when (value) {
            is EditTarget.Alias -> JsonPrimitive(value.alias)
            is EditTarget.NodeReference -> JsonPrimitive(value.nodeReference)
            is EditTarget.InModel -> JsonObject(
                mapOf(
                    "model" to JsonPrimitive(value.modelTarget),
                    "nodeId" to JsonPrimitive(value.nodeId),
                )
            )
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): EditTarget {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw UnsupportedJsonOnlySerializerException("EditTarget")
        val element = jsonDecoder.decodeJsonElement()

        (element as? JsonPrimitive)?.takeIf { it.isString }?.let { primitive ->
            val value = primitive.content
            return if (value.startsWith("$")) EditTarget.Alias(value) else EditTarget.NodeReference(value)
        }

        val targetObject = element as? JsonObject
            ?: throw ProtocolJsonException("edit target must be a node reference string or JSON object, got: $element")

        val nodeReference = targetObject.stringField("nodeReference")
        if (!nodeReference.isNullOrBlank()) {
            return EditTarget.NodeReference(nodeReference)
        }

        val modelTarget = targetObject.stringField("model") ?: targetObject.stringField("modelTarget")
        val nodeId = targetObject.stringField("nodeId")
        if (!modelTarget.isNullOrBlank() && !nodeId.isNullOrBlank()) {
            return EditTarget.InModel(modelTarget = modelTarget, nodeId = nodeId)
        }

        throw ProtocolJsonException("edit target requires a node reference string or model plus nodeId")
    }
}

@Serializable
data class EditConstraintViolation(
    val operation: Int,
    val constraint: String,
    val message: String,
)

/**
 * Reads a non-null JSON string property, or `null` when the property is absent or JSON null.
 */
internal fun JsonObject.stringField(name: String): String? =
    this[name]?.let { field -> if (field is JsonPrimitive && field.isString) field.content else null }
