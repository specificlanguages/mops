package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SEnumeration
import org.jetbrains.mps.openapi.language.SEnumerationLiteral
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil

/*
 * The string form of property values that Code Mode and edit batches read and write. It is the persisted string form,
 * except for enumeration-typed properties: there it is the literal name (`_0__1`), not the persisted `id/name` form. A
 * write accepts the literal name, or a presentation (`0..1`) that exactly one literal has; any other value fails with
 * InvalidPropertyValueException.
 */

/** Thrown when a property value string does not name a value of the property's data type. */
class InvalidPropertyValueException(message: String) : IllegalArgumentException(message)

/**
 * Reads [property] of [node] through MPS property getter handlers. An enumeration value that names no literal of the
 * enumeration is returned in its persisted form, which a write rejects.
 */
@Suppress("DEPRECATION")
fun readPropertyText(node: SNode, property: SProperty): String? {
    val enumeration = property.type as? SEnumeration ?: return SNodeAccessUtil.getProperty(node, property)
    return enumeration.literalName(SNodeAccessUtil.getPropertyValue(node, property))
        ?: SNodeAccessUtil.getProperty(node, property)
}

/** Reads the value of [property] stored in [node], without getter handlers, in the same form as [readPropertyText]. */
fun readStoredPropertyText(node: SNode, property: SProperty): String? {
    val stored = node.getProperty(property) ?: return null
    val enumeration = property.type as? SEnumeration ?: return stored
    return enumeration.literalName(enumeration.fromString(stored)) ?: stored
}

private fun SEnumeration.literalName(value: Any?): String? =
    (value as? SEnumerationLiteral)?.takeIf { it in literals }?.name

/**
 * Writes [property] of [node] through MPS property setter handlers, or clears it when [value] is null.
 *
 * Uses [SNodeAccessUtil.setProperty] (the String overload) rather than [SNodeAccessUtil.setPropertyValue] (the Object
 * overload) for non-enumeration values so the value goes through the property datatype's `fromString` conversion.
 * Passing a raw string to the Object overload silently fails for non-string datatypes: e.g. a boolean property handed
 * the string "true" (instead of a converted `Boolean`) ends up unset.
 */
@Suppress("DEPRECATION")
fun writePropertyText(node: SNode, property: SProperty, value: String?) {
    val enumeration = property.type as? SEnumeration
    when {
        value == null -> SNodeAccessUtil.setPropertyValue(node, property, null)
        enumeration != null -> SNodeAccessUtil.setPropertyValue(node, property, enumerationLiteral(property, enumeration, value))
        else -> SNodeAccessUtil.setProperty(node, property, value)
    }
}

private fun enumerationLiteral(property: SProperty, enumeration: SEnumeration, value: String): SEnumerationLiteral {
    val literals = enumeration.literals
    literals.firstOrNull { it.name == value }?.let { return it }
    val byPresentation = literals.filter { it.presentation == value }
    byPresentation.singleOrNull()?.let { return it }
    val listed = literals.joinToString { "${it.name} (${it.presentation})" }.ifEmpty { "none" }
    val ambiguity = if (byPresentation.isEmpty()) "" else "; it is the presentation of several literals, use a literal name"
    throw InvalidPropertyValueException(
        "invalid value '$value' for enumeration property ${property.name} of type ${enumeration.name}$ambiguity. " +
            "Valid literals: $listed",
    )
}
