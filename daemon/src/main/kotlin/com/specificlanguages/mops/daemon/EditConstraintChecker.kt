package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.EditConstraintViolation
import jetbrains.mps.core.aspects.constraints.rules.kinds.CanBeAncestorContext
import jetbrains.mps.core.aspects.constraints.rules.kinds.CanBeRootContext
import jetbrains.mps.core.aspects.constraints.rules.kinds.ContainmentContext
import jetbrains.mps.scope.ErrorScope
import jetbrains.mps.smodel.constraints.ConstraintsCanBeFacade
import jetbrains.mps.smodel.constraints.ModelConstraints
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.runtime.ReferenceScopeProvider
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Logic. Checks the nodes and references an edit batch placed against the Constraints of their languages, and records
 * what it could not check.
 *
 * Hides: which Constraints an edit is checked against and how each is evaluated through MPS.
 */
internal class EditConstraintChecker(
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    /**
     * Evaluates the Constraints a batch would violate, using only the cheap constraints/structural APIs (never the
     * typesystem or full Model Check). Covers the structural allowed-child concept, language-defined
     * can-be-child/parent/ancestor rules, containment cardinality, and language-defined reference **scope** rules.
     * Property-value Constraints are a later addition.
     */
    fun check(
        placements: List<Placement>,
        rootPlacements: List<RootPlacement>,
        referencePlacements: List<ReferencePlacement>,
        uncheckable: UncheckedConstraints,
    ): List<EditConstraintViolation> {
        val violations = mutableListOf<EditConstraintViolation>()
        val cardinalityChecked = hashSetOf<String>()

        for (rootPlacement in rootPlacements) {
            val rootConcept = rootPlacement.node.concept
            // A created/moved root whose concept did not load cannot run its can-be-root rule; record its language and
            // skip, mirroring the child-placement handling.
            if (!rootConcept.isValid) {
                uncheckable.add(rootConcept)
                continue
            }
            // The can-be-root path: only language-defined rules, never the typesystem or a full Model Check.
            if (ConstraintsCanBeFacade.checkCanBeRoot(CanBeRootContext(rootConcept, rootPlacement.model)).isNotEmpty()) {
                violations.add(
                    EditConstraintViolation(
                        rootPlacement.opIndex,
                        "canBeRoot",
                        "concept ${rootConcept.qualifiedName} cannot be a root of model ${rootPlacement.model.name.value}",
                    ),
                )
            }
        }

        for (placement in placements) {
            val childConcept = placement.child.concept
            // A created node whose concept did not load (e.g. a copy of a node from an uncompiled language) cannot be
            // checked against the containment rules, so record its language and skip this placement's checks.
            if (!childConcept.isValid) {
                uncheckable.add(childConcept)
                continue
            }
            val link = placement.link

            // Structural integrity: the child's concept must be a subconcept of the link's target concept. The can-be
            // facade below covers only language-defined rules, not this, so it is checked directly.
            if (!childConcept.isSubConceptOf(link.targetConcept)) {
                violations.add(
                    EditConstraintViolation(
                        operation = placement.opIndex,
                        constraint = "containment",
                        message = "concept ${childConcept.qualifiedName} is not allowed under role ${link.name} " +
                            "(expected ${link.targetConcept.qualifiedName})",
                    ),
                )
            }

            val containmentContext = ContainmentContext.Builder()
                .parentNode(placement.parent)
                .childNode(placement.child)
                .childConcept(childConcept)
                .link(link)
                .build()
            if (ConstraintsCanBeFacade.checkCanBeChild(containmentContext).isNotEmpty()) {
                violations.add(
                    EditConstraintViolation(
                        placement.opIndex,
                        "canBeChild",
                        "concept ${childConcept.qualifiedName} cannot be a child under role ${link.name}",
                    ),
                )
            }
            if (ConstraintsCanBeFacade.checkCanBeParent(containmentContext).isNotEmpty()) {
                violations.add(
                    EditConstraintViolation(
                        placement.opIndex,
                        "canBeParent",
                        "${placement.parent.concept.qualifiedName} cannot be the parent of ${childConcept.qualifiedName}",
                    ),
                )
            }
            // can-be-ancestor rules apply to every ancestor of the insertion point, so walk up from the parent.
            var ancestor: SNode? = placement.parent
            while (ancestor != null) {
                // An ancestor whose concept did not load cannot run its can-be-ancestor rule; record its language and
                // stop walking, since ancestors above it cannot be checked reliably either.
                if (!ancestor.concept.isValid) {
                    uncheckable.add(ancestor.concept)
                    break
                }
                val ancestorContext = CanBeAncestorContext.Builder()
                    .ancestorNode(ancestor)
                    .parentNode(placement.parent)
                    .childConcept(childConcept)
                    .link(link)
                    .build()
                if (ConstraintsCanBeFacade.checkCanBeAncestor(ancestorContext).isNotEmpty()) {
                    violations.add(
                        EditConstraintViolation(
                            placement.opIndex,
                            "canBeAncestor",
                            "concept ${childConcept.qualifiedName} cannot be a descendant of " +
                                ancestor.concept.qualifiedName,
                        ),
                    )
                    break
                }
                ancestor = ancestor.parent
            }

            // Cardinality: a single-valued role may hold at most one child, evaluated once per touched parent+link.
            val cardinalityKey = "${persistence.asString(placement.parent.reference)}#${link.name}"
            if (cardinalityChecked.add(cardinalityKey) && !link.isMultiple) {
                val count = placement.parent.getChildren(link).count()
                if (count > 1) {
                    violations.add(
                        EditConstraintViolation(
                            placement.opIndex,
                            "cardinality",
                            "role ${link.name} is single-valued but would hold $count children",
                        ),
                    )
                }
            }

            // An obligatory containment role on a created node must not be left empty.
            for (childLink in childConcept.containmentLinks) {
                if (!childLink.isOptional && placement.child.getChildren(childLink).none()) {
                    violations.add(
                        EditConstraintViolation(
                            placement.opIndex,
                            "cardinality",
                            "obligatory role ${childLink.name} on ${childConcept.qualifiedName} is empty",
                        ),
                    )
                }
            }
        }

        for (referencePlacement in referencePlacements) {
            val sourceConcept = referencePlacement.node.concept
            // A node whose concept did not load cannot run its reference-scope rule; record its language and skip.
            if (!sourceConcept.isValid) {
                uncheckable.add(sourceConcept)
                continue
            }
            val link = referencePlacement.link
            // Only language-defined reference scopes are validated, mirroring the can-be-* rules: both run arbitrary
            // language code, and both fire only where a language actually defines the rule. When no scope provider is
            // defined MPS falls back to a global default scope; validating against that is a reference-resolution
            // (Model Check) concern rather than a language Constraint, so it is left unchecked here.
            if (!referenceScopeIsDefined(sourceConcept, link)) {
                continue
            }
            val scope = ModelConstraints.getReferenceDescriptor(referencePlacement.node, link).scope
            // The scope function threw: MPS returns an ErrorScope rather than a membership answer, so treat the
            // reference as unable-to-check (a language defect) rather than fabricating a scope violation. Its language
            // did load, so it is reported as a warning whether or not missing languages are ignored.
            if (scope is ErrorScope) {
                uncheckable.addFailedScope(link, sourceConcept)
                continue
            }
            if (!scope.contains(referencePlacement.target)) {
                violations.add(
                    EditConstraintViolation(
                        referencePlacement.opIndex,
                        "referenceScope",
                        "target ${persistence.asString(referencePlacement.target.reference)} is not in the allowed " +
                            "scope of reference ${link.name} on ${sourceConcept.qualifiedName}",
                    ),
                )
            }
        }
        return violations
    }

    /**
     * Whether a language defines a scope for [link] on [sourceConcept], resolved exactly as MPS does: a reference-level
     * scope provider takes precedence, otherwise the default scope provider registered on the link's target concept. A
     * null result means only the global default search scope would apply, which this executor does not enforce.
     */
    private fun referenceScopeIsDefined(sourceConcept: SConcept, link: SReferenceLink): Boolean {
        val registry = ConceptRegistry.getInstance()
        val referenceProvider: ReferenceScopeProvider? =
            registry.getConstraintsDescriptor(sourceConcept).getReference(link)?.scopeProvider
        if (referenceProvider != null) {
            return true
        }
        return registry.getConstraintsDescriptor(link.targetConcept).defaultScopeProvider != null
    }
}

internal class Placement(
    val opIndex: Int,
    val parent: SNode,
    val link: SContainmentLink,
    val child: SNode,
)

/**
 * A node placed into Root Node position of [model] by a root Edit Operation, checked against the can-be-root Constraint.
 */
internal class RootPlacement(
    val opIndex: Int,
    val model: SModel,
    val node: SNode,
)

/**
 * A concrete reference target set on [node] via [link] by an Edit Operation, checked against the language-defined
 * reference-scope Constraint. Cleared references (null target) are not recorded.
 */
internal class ReferencePlacement(
    val opIndex: Int,
    val node: SNode,
    val link: SReferenceLink,
    val target: SNode,
)

/**
 * Collects the constraints an edit batch could not check: concepts whose languages are not loaded, grouped by language
 * so each language is reported once, and reference scopes whose scope function failed.
 */
internal class UncheckedConstraints {
    private val conceptsByLanguage = linkedMapOf<String, MutableSet<String>>()
    private val failedScopes = linkedSetOf<String>()

    fun add(concept: SConcept) {
        conceptsByLanguage.getOrPut(ConceptValidityGuard.languageName(concept)) { linkedSetOf() }
            .add(concept.qualifiedName)
    }

    fun addFailedScope(link: SReferenceLink, concept: SConcept) {
        failedScopes.add(
            "reference scope of ${link.name} on ${concept.qualifiedName} was not checked because its scope function " +
                "failed",
        )
    }

    val hasMissingLanguages: Boolean get() = conceptsByLanguage.isNotEmpty()

    fun failureMessage(): String = missingLanguagesFailureMessage(conceptsByLanguage)

    fun warnings(): List<String> = uncheckableLanguageWarnings(conceptsByLanguage.keys.toList()) + failedScopes
}

/**
 * Formats the failure for constraints that could not be checked because their languages are not loaded, naming each
 * language with the concepts that needed it.
 */
internal fun missingLanguagesFailureMessage(conceptsByLanguage: Map<String, Set<String>>): String {
    val languages = conceptsByLanguage.entries.joinToString { (language, concepts) ->
        "$language (${concepts.joinToString()})"
    }
    return "constraints could not be checked because these languages are not loaded: $languages — build them " +
        "(e.g. 'mops make module ${conceptsByLanguage.keys.joinToString(" ")}') and retry, or rerun with " +
        "--ignore-missing-languages to skip the unchecked constraints"
}

/**
 * Formats one warning per language whose constraints were skipped, capped at [MAX_DETAILED_LANGUAGE_WARNINGS] with a
 * final summary line when more languages were affected.
 */
internal fun uncheckableLanguageWarnings(languages: List<String>): List<String> {
    val shown = languages.take(MAX_DETAILED_LANGUAGE_WARNINGS).map {
        "constraints for language '$it' were not checked because it is not loaded"
    }
    return if (languages.size > MAX_DETAILED_LANGUAGE_WARNINGS) {
        shown + "…and ${languages.size - MAX_DETAILED_LANGUAGE_WARNINGS} more languages could not be checked because they are not loaded"
    } else {
        shown
    }
}

private const val MAX_DETAILED_LANGUAGE_WARNINGS = 5
