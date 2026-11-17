package com.specificlanguages.mops.daemon

/** The kinds of node features (properties and roles of a concept), with the words that describe them. */
internal enum class NodeFeatureKind(val plural: String) {
    PROPERTY("properties"),
    CHILD_ROLE("child roles"),
    REFERENCE_ROLE("reference roles"),
}
