package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode

/**
 * The kinds of node features (properties and roles of a concept), with the words that describe them and the error codes
 * of their lookup errors.
 */
internal enum class NodeFeatureKind(
    val singular: String,
    val plural: String,
    val notFoundCode: MpsErrorCode,
    val ambiguousCode: MpsErrorCode,
) {
    PROPERTY("property", "properties", MpsErrorCode.PROPERTY_NOT_FOUND, MpsErrorCode.AMBIGUOUS_PROPERTY),
    CHILD_ROLE("child role", "child roles", MpsErrorCode.ROLE_NOT_FOUND, MpsErrorCode.AMBIGUOUS_ROLE),
    REFERENCE_ROLE("reference role", "reference roles", MpsErrorCode.ROLE_NOT_FOUND, MpsErrorCode.AMBIGUOUS_ROLE),
}
