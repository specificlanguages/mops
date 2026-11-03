package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode

/** The child role named [role] of this node's concept, including inherited roles. */
internal fun SNode.requireContainmentLink(role: String): SContainmentLink =
    requireFeature("child role", "child roles", role, concept.containmentLinks) { it.name }

/** The reference role named [role] of this node's concept, including inherited roles. */
internal fun SNode.requireReferenceLink(role: String): SReferenceLink =
    requireFeature("reference role", "reference roles", role, concept.referenceLinks) { it.name }

/** The property named [name] of this node's concept, including inherited properties. */
internal fun SNode.requireProperty(name: String): SProperty =
    requireFeature("property", "properties", name, concept.properties) { it.name }

/**
 * Finds the feature named [name] or fails. An unresolved concept has no features at all, so it fails with the
 * unloaded language instead of calling [name] unknown.
 */
@Suppress("DEPRECATION")
private fun <T> SNode.requireFeature(
    kind: String,
    kindPlural: String,
    name: String,
    features: Collection<T>,
    nameOf: (T) -> String,
): T {
    ConceptValidityGuard.requireValid(concept, "$kind '$name'")
    return features.firstOrNull { nameOf(it) == name } ?: throw IllegalArgumentException(
        "concept ${concept.qualifiedName} has no $kind '$name'; its $kindPlural: " +
            features.map(nameOf).sorted().joinToString().ifEmpty { "none" }
    )
}
