/**
 * Logic. Finds a property, child role or reference role of a node's concept by name, or checks that the concept has a
 * given feature descriptor. Code Mode accessors and edit batches both look up node features here.
 *
 * Hides: how a name selects a node feature (the exact name among the concept's own and inherited features), and the
 * unloaded-concept, not-found and ambiguous-name errors with their codes.
 */
package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode

/** A node feature with its name and the qualified name of the concept that declares it. */
internal data class NamedNodeFeature<T>(val feature: T, val name: String, val owner: String)

/** The child role named [role] of this node's concept, including inherited roles. */
@Suppress("DEPRECATION")
internal fun SNode.requireContainmentLink(role: String): SContainmentLink =
    requireNodeFeature(NodeFeatureKind.CHILD_ROLE, role) {
        concept.containmentLinks.map { NamedNodeFeature(it, it.name, it.owner.qualifiedName) }
    }

/** The reference role named [role] of this node's concept, including inherited roles. */
@Suppress("DEPRECATION")
internal fun SNode.requireReferenceLink(role: String): SReferenceLink =
    requireNodeFeature(NodeFeatureKind.REFERENCE_ROLE, role) {
        concept.referenceLinks.map { NamedNodeFeature(it, it.name, it.owner.qualifiedName) }
    }

/** The property named [name] of this node's concept, including inherited properties. */
@Suppress("DEPRECATION")
internal fun SNode.requireProperty(name: String): SProperty =
    requireNodeFeature(NodeFeatureKind.PROPERTY, name) {
        concept.properties.map { NamedNodeFeature(it, it.name, it.owner.qualifiedName) }
    }

/** [link] if it is one of this node's concept's child roles, including inherited roles. */
@Suppress("DEPRECATION")
internal fun SNode.requireContainmentLink(link: SContainmentLink): SContainmentLink =
    requireNodeFeature(NodeFeatureKind.CHILD_ROLE, link, link.name, link.owner.qualifiedName) { concept.containmentLinks }

/** [link] if it is one of this node's concept's reference roles, including inherited roles. */
@Suppress("DEPRECATION")
internal fun SNode.requireReferenceLink(link: SReferenceLink): SReferenceLink =
    requireNodeFeature(NodeFeatureKind.REFERENCE_ROLE, link, link.name, link.owner.qualifiedName) { concept.referenceLinks }

/** [property] if it is one of this node's concept's properties, including inherited properties. */
@Suppress("DEPRECATION")
internal fun SNode.requireProperty(property: SProperty): SProperty =
    requireNodeFeature(NodeFeatureKind.PROPERTY, property, property.name, property.owner.qualifiedName) { concept.properties }

private fun <T> SNode.requireNodeFeature(
    kind: NodeFeatureKind,
    name: String,
    features: () -> List<NamedNodeFeature<T>>,
): T {
    requireConceptLoaded(kind, name)
    return requireNamedNodeFeature(kind, concept.qualifiedName, name, features())
}

private fun <T> SNode.requireNodeFeature(
    kind: NodeFeatureKind,
    feature: T,
    name: String,
    owner: String,
    features: () -> Iterable<T>,
): T {
    requireConceptLoaded(kind, name)
    if (feature !in features()) {
        throw MpsRequestException(
            kind.notFoundCode,
            "concept ${concept.qualifiedName} has no ${kind.singular} '$name' declared by $owner",
        )
    }
    return feature
}

/**
 * An unresolved concept has no features at all, so it fails with the unloaded language instead of calling [name]
 * unknown.
 */
@Suppress("DEPRECATION")
private fun SNode.requireConceptLoaded(kind: NodeFeatureKind, name: String) {
    if (!concept.isValid) {
        throw MpsRequestException(
            MpsErrorCode.LANGUAGE_NOT_LOADED,
            "cannot look up ${kind.singular} '$name': ${ConceptValidityGuard.notLoadedMessage(concept)}",
        )
    }
}

/** The one feature among [features] named [name], or an [MpsRequestException] that lists the candidates. */
internal fun <T> requireNamedNodeFeature(
    kind: NodeFeatureKind,
    conceptName: String,
    name: String,
    features: List<NamedNodeFeature<T>>,
): T {
    val matches = features.filter { it.name == name }
    return when (matches.size) {
        1 -> matches.single().feature
        0 -> throw MpsRequestException(
            kind.notFoundCode,
            "concept $conceptName has no ${kind.singular} '$name'; its ${kind.plural}: " +
                features.map { it.name }.distinct().sorted().joinToString().ifEmpty { "none" },
        )
        else -> throw MpsRequestException(
            kind.ambiguousCode,
            "concept $conceptName has ${matches.size} ${kind.plural} named '$name', declared by " +
                matches.map { it.owner }.sorted().joinToString(),
        )
    }
}
