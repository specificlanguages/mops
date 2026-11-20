package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink

/**
 * The kinds of node features (properties and roles of a concept), with the MPS descriptor type of each, the words that
 * describe them, and the error codes of their lookup errors.
 */
internal enum class NodeFeatureKind(
    val descriptor: Class<*>,
    val singular: String,
    val plural: String,
    val notFoundCode: MpsErrorCode,
    val ambiguousCode: MpsErrorCode,
) {
    PROPERTY(
        SProperty::class.java, "property", "properties",
        MpsErrorCode.PROPERTY_NOT_FOUND, MpsErrorCode.AMBIGUOUS_PROPERTY,
    ),
    CHILD_ROLE(
        SContainmentLink::class.java, "child role", "child roles",
        MpsErrorCode.ROLE_NOT_FOUND, MpsErrorCode.AMBIGUOUS_ROLE,
    ),
    REFERENCE_ROLE(
        SReferenceLink::class.java, "reference role", "reference roles",
        MpsErrorCode.ROLE_NOT_FOUND, MpsErrorCode.AMBIGUOUS_ROLE,
    ),
}
