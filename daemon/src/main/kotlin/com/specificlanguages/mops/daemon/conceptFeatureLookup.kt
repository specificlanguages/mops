/**
 * Logic. Finds a property, child role or reference role of a node's concept by name. Code Mode accessors and edit
 * batches both look up features here.
 *
 * Hides: how a name selects a feature (the exact name among the concept's own and inherited features), and the
 * unloaded-concept, not-found and ambiguous-name errors.
 */
package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode

/** The child role named [role] of this node's concept, including inherited roles. */
internal fun SNode.requireContainmentLink(role: String): SContainmentLink =
    requireFeature(NodeFeatureKind.CHILD_ROLE, role, concept.containmentLinks) { it.name to it.owner.qualifiedName }

/** The reference role named [role] of this node's concept, including inherited roles. */
internal fun SNode.requireReferenceLink(role: String): SReferenceLink =
    requireFeature(NodeFeatureKind.REFERENCE_ROLE, role, concept.referenceLinks) { it.name to it.owner.qualifiedName }

/** The property named [name] of this node's concept, including inherited properties. */
internal fun SNode.requireProperty(name: String): SProperty =
    requireFeature(NodeFeatureKind.PROPERTY, name, concept.properties) { it.name to it.owner.qualifiedName }

/**
 * An unresolved concept has no features at all, so it fails with [ConceptNotLoadedException], naming the unloaded
 * language, instead of calling [name] unknown.
 */
private fun <T> SNode.requireFeature(
    kind: NodeFeatureKind,
    name: String,
    features: Iterable<T>,
    nameAndOwner: (T) -> Pair<String, String>,
): T {
    if (!concept.isValid) {
        throw ConceptNotLoadedException(
            "cannot look up ${kind.singular} '$name': ${ConceptValidityGuard.notLoadedMessage(concept)}",
        )
    }
    return requireNamedFeature(kind, concept.qualifiedName, name, features, nameAndOwner)
}

/**
 * The one feature among [features] named [name], or a [NodeFeatureLookupException] that lists the candidates.
 * [nameAndOwner] gives a feature's name and the qualified name of the concept that declares it.
 */
internal fun <T> requireNamedFeature(
    kind: NodeFeatureKind,
    conceptName: String,
    name: String,
    features: Iterable<T>,
    nameAndOwner: (T) -> Pair<String, String>,
): T {
    val nameOf = { feature: T -> nameAndOwner(feature).first }
    val matches = features.filter { nameOf(it) == name }
    return when (matches.size) {
        1 -> matches.single()
        0 -> throw NodeFeatureLookupException(
            kind.notFoundCode,
            "concept $conceptName has no ${kind.singular} '$name'; its ${kind.plural}: " +
                features.map(nameOf).sorted().joinToString().ifEmpty { "none" },
        )
        else -> throw NodeFeatureLookupException(
            kind.ambiguousCode,
            "concept $conceptName has ${matches.size} ${kind.plural} named '$name', declared by " +
                matches.map { nameAndOwner(it).second }.sorted().joinToString(),
        )
    }
}
