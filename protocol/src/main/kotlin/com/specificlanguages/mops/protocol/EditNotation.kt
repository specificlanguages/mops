package com.specificlanguages.mops.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames

/**
 * Plain-Kotlin introspection over the edit-batch Notation.
 *
 * Reads the serialized shape of [EditOperation] from its generated serial descriptors so callers (help text, JSON-Schema
 * emitters, error messages) stay in sync with the wire format without depending on serialization types. The public
 * signatures expose only [String] and [List], so the CLI module can consume this through the thin protocol jar even
 * though the serialization runtime is relocated inside the shaded jar.
 */
@OptIn(ExperimentalSerializationApi::class)
object EditNotation {
    // EditBatch -> List<EditOperation> -> EditOperation (sealed) -> polymorphic subclass container. The container's
    // element names are the leaf @SerialName values and each element descriptor is that leaf's class descriptor.
    private val operationsContainer: SerialDescriptor =
        EditBatch.serializer().descriptor
            .getElementDescriptor(0)
            .getElementDescriptor(0)
            .getElementDescriptor(1)

    /** The `op` discriminator values of the edit operations, in declaration order. */
    val operationNames: List<String> = operationsContainer.elementNames.toList()

    /**
     * The serialized field names of one operation, identified by its `op` value, excluding the `op` discriminator.
     * Includes optional fields and honors `@SerialName` (for example the alias field is reported as `as`).
     */
    fun serializedFieldNames(op: String): List<String> =
        leafDescriptor(op).elementNames.toList().filter { it != "op" }

    /**
     * The serialized names of the required (non-optional) fields of one operation, excluding the `op` discriminator.
     * Derived from descriptor optionality, so a field that gains or loses a default value stays in sync automatically.
     */
    fun requiredFieldNames(op: String): List<String> {
        val leaf = leafDescriptor(op)
        return (0 until leaf.elementsCount)
            .filter { leaf.getElementName(it) != "op" && !leaf.isElementOptional(it) }
            .map { leaf.getElementName(it) }
    }

    /**
     * The serial name of a field's declared type (custom-serializer types report `EditTarget` / `ChildPosition`; a
     * nullable field carries a trailing `?`), or `null` when the operation has no such field.
     */
    fun fieldTypeName(op: String, field: String): String? {
        val leaf = leafDescriptor(op)
        val index = leaf.elementNames.indexOf(field)
        return if (index < 0) null else leaf.getElementDescriptor(index).serialName
    }

    private fun leafDescriptor(op: String): SerialDescriptor {
        val index = operationsContainer.elementNames.indexOf(op)
        require(index >= 0) { "unknown edit operation: $op" }
        return operationsContainer.getElementDescriptor(index)
    }
}
