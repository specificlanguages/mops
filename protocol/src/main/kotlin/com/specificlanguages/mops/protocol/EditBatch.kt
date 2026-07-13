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
 * Ordered batch of Edit Operations applied as a unit by `mops model edit`, with best-effort atomicity.
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

    @Serializable
    @SerialName("setProperty")
    data class SetProperty(
        val target: EditTarget,
        val name: String,
        val value: String? = null,
    ) : EditOperation

    @Serializable
    @SerialName("delete")
    data class Delete(val target: EditTarget) : EditOperation

    @Serializable
    @SerialName("deleteChild")
    data class DeleteChild(
        val target: EditTarget,
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
        val target: EditTarget,
        val role: String,
        val concept: String,
        val properties: List<MpsNodePropertyJson>? = null,
        val references: List<InlineReference>? = null,
        val children: List<InlineChild>? = null,
        val position: ChildPosition = ChildPosition.Last,
        @SerialName("as") override val alias: String? = null,
    ) : EditOperation, CreatingOperation

    /**
     * Moves the [target] node under the [into] parent's containment [role] at [position], detaching it from its
     * current location.
     */
    @Serializable
    @SerialName("moveAsChild")
    data class MoveAsChild(
        val target: EditTarget,
        val into: EditTarget,
        val role: String,
        val position: ChildPosition = ChildPosition.Last,
    ) : EditOperation

    /**
     * Sets the [target] node's reference [role] to point at [to], or clears it when [to] is null. The [to] node may
     * live in another model.
     */
    @Serializable
    @SerialName("setReference")
    data class SetReference(
        val target: EditTarget,
        val role: String,
        val to: EditTarget? = null,
    ) : EditOperation

    /**
     * Deep-copies [source] into the [target] parent's containment [role] at [position], assigning the copy fresh node
     * ids so it is a distinct node rather than a duplicate identity. [source] may live in a read-only model.
     */
    @Serializable
    @SerialName("copyAsChild")
    data class CopyAsChild(
        val target: EditTarget,
        val source: EditTarget,
        val role: String,
        val position: ChildPosition = ChildPosition.Last,
        @SerialName("as") override val alias: String? = null,
    ) : EditOperation, CreatingOperation

    /**
     * Creates a new Root Node of [concept] directly under the destination [model]. Unlike [AddChild], a Root Node is
     * owned by the model rather than a containing link, so there is no role and no position. The new node may carry an
     * inline [properties] / [references] / [children] subtree in the same shape `get-node` emits.
     */
    @Serializable
    @SerialName("addRoot")
    data class AddRoot(
        val model: ModelDestination,
        val concept: String,
        val properties: List<MpsNodePropertyJson>? = null,
        val references: List<InlineReference>? = null,
        val children: List<InlineChild>? = null,
        @SerialName("as") override val alias: String? = null,
    ) : EditOperation, CreatingOperation

    /**
     * Deep-copies [source] into the destination [model] as a new Root Node, assigning the copy fresh node ids. [source]
     * may be a root or a child and may live in a read-only model.
     */
    @Serializable
    @SerialName("copyAsRoot")
    data class CopyAsRoot(
        val model: ModelDestination,
        val source: EditTarget,
        @SerialName("as") override val alias: String? = null,
    ) : EditOperation, CreatingOperation

    /**
     * Moves the [target] node — a child or an existing root — to Root Node position of the destination [model],
     * detaching it from its current location.
     */
    @Serializable
    @SerialName("moveAsRoot")
    data class MoveAsRoot(
        val target: EditTarget,
        val model: ModelDestination,
    ) : EditOperation

    /**
     * Replaces the [target] node in place with [with], an Inline Subtree position: a fresh-node spec, a Move Leaf that
     * adopts an existing node identity-preservingly, or a Copy Leaf that deep-copies with fresh ids. The replacement
     * takes the target's exact slot — same parent, Containment Role, and sibling index, or Root Node position in the
     * same model when the target is a root — and the remainder of the target's old subtree is deleted. A bare Move Leaf
     * of a descendant of [target] is an unwrap. Any [role] carried by [with] is ignored: the slot comes from the target.
     */
    @Serializable
    @SerialName("replace")
    data class Replace(
        val target: EditTarget,
        val with: InlineChild,
        @SerialName("as") override val alias: String? = null,
    ) : EditOperation, CreatingOperation

    /**
     * Wraps the [target] node in a fresh node of [concept] that takes the target's exact place (same parent,
     * Containment Role, and sibling index, or Root Node position when the target is a root); the target then moves
     * under the wrapper's [role] at [position] among any inline-built siblings. The wrapper is built like an [AddChild]
     * inline spec, carrying [properties], [references], and a [children] subtree. [alias] binds the wrapper.
     */
    @Serializable
    @SerialName("wrap")
    data class Wrap(
        val target: EditTarget,
        val concept: String,
        val role: String,
        val properties: List<MpsNodePropertyJson>? = null,
        val references: List<InlineReference>? = null,
        val children: List<InlineChild>? = null,
        val position: ChildPosition = ChildPosition.Last,
        @SerialName("as") override val alias: String? = null,
    ) : EditOperation, CreatingOperation

    /**
     * Unwraps the [target] node, promoting [keep] — an [EditTarget] that must resolve to a proper descendant of
     * [target] — into the target's exact place (or Root Node position when the target is a root). The rest of the
     * target's subtree is deleted. Nothing is created, so there is no alias.
     */
    @Serializable
    @SerialName("unwrap")
    data class Unwrap(
        val target: EditTarget,
        val keep: EditTarget,
    ) : EditOperation
}

/**
 * An [EditOperation] that creates a node and may bind it to a batch-local [alias] (a `$`-prefixed name) that later
 * operations can target and that the response reports under `created`.
 */
interface CreatingOperation {
    val alias: String?
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
 * Target of an edit operation. Encoded either as a bare reference string (a `$`-prefixed alias, an alias with a
 * descending role path, or a node reference) or as a `{model, nodeId}` / `{base, path}` object; decoding mirrors that
 * shape.
 */
@Serializable(with = EditTargetSerializer::class)
sealed interface EditTarget {
    data class NodeReference(val nodeReference: String) : EditTarget
    data class InModel(val modelTarget: String, val nodeId: String) : EditTarget
    data class Alias(val alias: String) : EditTarget

    /**
     * A node addressed relative to a batch-local [alias] (its `$`-prefixed name) by a [path] of Containment Role steps
     * descending from it — e.g. `"$copy/left"` or `{"base": "$copy", "path": "operands[1]/name"}`. Lets a later
     * operation reach inside a freshly created subtree without knowing the descendant's id. [alias] carries the `$`
     * sigil, matching [Alias].
     */
    data class RelativeAlias(val alias: String, val path: List<PathStep>) : EditTarget

    /**
     * One step of a [RelativeAlias] path: descend the Containment [role] to the child at [position]. A bare `role`
     * segment defaults to [ChildPosition.Only]; a bracketed `role[first|last|only|<int>]` names another position,
     * following the `explain position` notation.
     */
    data class PathStep(val role: String, val position: ChildPosition)
}

/**
 * Parses a [EditTarget.RelativeAlias] path — `/`-separated role segments, each `role` or `role[position]` — into its
 * steps. Throws [ProtocolJsonException] on an empty path, an empty segment, or a malformed position.
 */
internal fun parseAliasPath(pathString: String): List<EditTarget.PathStep> {
    if (pathString.isEmpty()) throw ProtocolJsonException("relative edit target path is empty")
    return pathString.split("/").map { segment ->
        if (segment.isEmpty()) throw ProtocolJsonException("relative edit target path has an empty segment: \"$pathString\"")
        parseAliasPathSegment(segment)
    }
}

private fun parseAliasPathSegment(segment: String): EditTarget.PathStep {
    val open = segment.indexOf('[')
    if (open < 0) {
        return EditTarget.PathStep(segment, ChildPosition.Only)
    }
    if (!segment.endsWith("]")) {
        throw ProtocolJsonException("relative edit target path segment is malformed (expected role[position]): \"$segment\"")
    }
    val role = segment.substring(0, open)
    if (role.isEmpty()) throw ProtocolJsonException("relative edit target path segment has no role: \"$segment\"")
    val token = segment.substring(open + 1, segment.length - 1)
    val position = when (token) {
        "first" -> ChildPosition.First
        "last" -> ChildPosition.Last
        "only" -> ChildPosition.Only
        else -> token.toIntOrNull()?.let { ChildPosition.Index(it) }
            ?: throw ProtocolJsonException(
                "relative edit target position must be first, last, only, or an integer: \"$token\"",
            )
    }
    return EditTarget.PathStep(role, position)
}

/** Renders a [EditTarget.RelativeAlias] back to its canonical `$alias/role[position]/…` string. */
private fun renderRelativeAlias(target: EditTarget.RelativeAlias): String {
    val sigiled = if (target.alias.startsWith("$")) target.alias else "$${target.alias}"
    val path = target.path.joinToString("/") { step ->
        when (val position = step.position) {
            ChildPosition.Only -> step.role
            ChildPosition.First -> "${step.role}[first]"
            ChildPosition.Last -> "${step.role}[last]"
            is ChildPosition.Index -> "${step.role}[${position.index}]"
        }
    }
    return "$sigiled/$path"
}

internal object EditTargetSerializer : KSerializer<EditTarget> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EditTarget")

    override fun serialize(encoder: Encoder, value: EditTarget) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw UnsupportedJsonOnlySerializerException("EditTarget")
        val element = when (value) {
            is EditTarget.Alias -> JsonPrimitive(value.alias)
            is EditTarget.RelativeAlias -> JsonPrimitive(renderRelativeAlias(value))
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
            if (!value.startsWith("$")) return EditTarget.NodeReference(value)
            // A '$'-prefixed string is an alias; a '/' after the alias name descends a role path into it.
            val slash = value.indexOf('/')
            return if (slash < 0) {
                EditTarget.Alias(value)
            } else {
                EditTarget.RelativeAlias(value.substring(0, slash), parseAliasPath(value.substring(slash + 1)))
            }
        }

        val targetObject = element as? JsonObject
            ?: throw ProtocolJsonException("edit target must be a node reference string or JSON object, got: $element")

        val base = targetObject.stringField("base")
        if (base != null) {
            val pathString = targetObject.stringField("path")
                ?: throw ProtocolJsonException("relative edit target requires a \"path\" string alongside \"base\"")
            val alias = if (base.startsWith("$")) base else "$$base"
            return EditTarget.RelativeAlias(alias, parseAliasPath(pathString))
        }

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

/**
 * Destination model of a root Edit Operation. Encoded either as a bare model target string (a serialized model
 * reference, model name, or file path) or as a `{model: <model target>}` object; decoding mirrors that shape. This is
 * the same model grammar `get-node` and `find` accept, restricted to the model part (a root has no node target).
 */
@Serializable(with = ModelDestinationSerializer::class)
data class ModelDestination(val modelTarget: String)

internal object ModelDestinationSerializer : KSerializer<ModelDestination> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ModelDestination")

    override fun serialize(encoder: Encoder, value: ModelDestination) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw UnsupportedJsonOnlySerializerException("ModelDestination")
        jsonEncoder.encodeJsonElement(JsonPrimitive(value.modelTarget))
    }

    override fun deserialize(decoder: Decoder): ModelDestination {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw UnsupportedJsonOnlySerializerException("ModelDestination")
        val element = jsonDecoder.decodeJsonElement()

        (element as? JsonPrimitive)?.takeIf { it.isString }?.let { primitive ->
            return ModelDestination(primitive.content)
        }

        val destinationObject = element as? JsonObject
            ?: throw ProtocolJsonException("model destination must be a model target string or JSON object, got: $element")

        val modelTarget = destinationObject.stringField("model") ?: destinationObject.stringField("modelTarget")
        if (!modelTarget.isNullOrBlank()) {
            return ModelDestination(modelTarget)
        }

        throw ProtocolJsonException("model destination requires a model target string or a model field")
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
