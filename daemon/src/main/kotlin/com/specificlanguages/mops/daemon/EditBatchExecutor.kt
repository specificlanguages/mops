package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ChildPosition
import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditConstraintViolation
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.NodeTarget
import jetbrains.mps.core.aspects.constraints.rules.kinds.CanBeAncestorContext
import jetbrains.mps.core.aspects.constraints.rules.kinds.CanBeRootContext
import jetbrains.mps.core.aspects.constraints.rules.kinds.ContainmentContext
import jetbrains.mps.project.Project
import jetbrains.mps.scope.ErrorScope
import jetbrains.mps.smodel.CopyUtil
import jetbrains.mps.smodel.constraints.ConstraintsCanBeFacade
import jetbrains.mps.smodel.constraints.ModelConstraints
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.runtime.ReferenceScopeProvider
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessUtil
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

class EditBatchExecutor(
    private val modelNodeResolver: ModelNodeResolver,
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun apply(
        project: Project,
        batch: EditBatch,
        writeScope: WriteTransaction.WriteScope,
        enforcement: ConstraintEnforcement = ConstraintEnforcement.BEST_EFFORT,
    ): ModelEditResponse {
        if (batch.operations.isEmpty()) {
            throw MpsRequestException(
                code = MpsErrorCode.INVALID_REQUEST,
                message = "edit batch must contain at least one operation",
            )
        }

        val affectedModels = linkedSetOf<EditableSModel>()
        val aliases = linkedMapOf<String, SNode>()
        val placements = mutableListOf<Placement>()
        val rootPlacements = mutableListOf<RootPlacement>()
        val referencePlacements = mutableListOf<ReferencePlacement>()
        val uncheckable = UncheckableLanguages()
        var mutated = false

        fun fail(code: MpsErrorCode, message: String): Nothing {
            if (mutated) {
                reload(affectedModels)
            }
            throw MpsRequestException(code = code, message = message)
        }

        // Resolves any target form to a node: a batch-local alias (must already be bound), a serialized node reference,
        // or a model plus node id. A forward reference to an unbound alias fails.
        fun resolveNode(index: Int, target: EditTarget): SNode {
            if (target is EditTarget.Alias) {
                return aliases[aliasName(target.alias)]
                    ?: fail(
                        MpsErrorCode.TARGET_RESOLUTION_FAILED,
                        "operation $index alias is not defined earlier in the batch: ${target.alias}",
                    )
            }
            val node = try {
                resolveTarget(project, target)
            } catch (exception: Exception) {
                fail(
                    MpsErrorCode.TARGET_RESOLUTION_FAILED,
                    "operation $index target could not be resolved: ${exception.message ?: exception.javaClass.name}",
                )
            }
            val resolved = node ?: fail(MpsErrorCode.NODE_NOT_FOUND, "operation $index target node not found")
            // A node whose concept did not resolve (uncompiled language) carries only placeholder concept information.
            // Under strict enforcement that aborts the batch; otherwise it is recorded as an unchecked language and the
            // operation proceeds (and typically fails later at property/role resolution, which is fine).
            if (!resolved.concept.isValid) {
                if (enforcement == ConstraintEnforcement.STRICT) {
                    fail(MpsErrorCode.LANGUAGE_NOT_LOADED, "operation $index target: ${ConceptValidityGuard.messageFor(resolved)}")
                }
                uncheckable.add(resolved.concept)
            }
            return resolved
        }

        // Binds a created node to a batch-local alias; a duplicate alias name fails. The name is stored without its
        // reference sigil, so binding as "$root" and binding as "root" collide.
        fun bindAlias(index: Int, alias: String?, node: SNode) {
            if (alias == null) {
                return
            }
            if (aliases.putIfAbsent(aliasName(alias), node) != null) {
                fail(MpsErrorCode.INVALID_REQUEST, "operation $index alias is already defined: $alias")
            }
        }

        // Resolves a target whose model must be editable, clean at batch start, and tracked as affected.
        fun requireEditableNode(index: Int, target: EditTarget): SNode {
            val node = resolveNode(index, target)
            val model = node.model
                ?: fail(MpsErrorCode.NODE_NOT_FOUND, "operation $index target node is detached from a model")
            val editableModel = writeScope.asEditable(model)
                ?: fail(
                    MpsErrorCode.MODEL_READ_ONLY,
                    "operation $index target model is not editable: ${model.name.longName}",
                )
            if (editableModel !in affectedModels) {
                if (editableModel.isChanged) {
                    fail(
                        MpsErrorCode.MODEL_CHANGED,
                        "operation $index target model has unsaved changes: ${editableModel.name.longName}",
                    )
                }
                affectedModels.add(editableModel)
            }
            return node
        }

        // Resolves a root operation's destination model, which must resolve uniquely, be editable, clean at batch start,
        // and tracked as affected. Mirrors requireEditableNode but addresses a model rather than a node.
        fun requireEditableModel(index: Int, destination: ModelDestination): SModel {
            val model = try {
                modelNodeResolver.findModelUnique(project, destination.modelTarget)
            } catch (exception: MpsRequestException) {
                fail(exception.code, "operation $index destination model: ${exception.message}")
            } catch (exception: Exception) {
                fail(
                    MpsErrorCode.TARGET_RESOLUTION_FAILED,
                    "operation $index destination model could not be resolved: ${exception.message ?: exception.javaClass.name}",
                )
            } ?: fail(MpsErrorCode.MODEL_NOT_FOUND, "operation $index destination model not found: ${destination.modelTarget}")

            val editableModel = writeScope.asEditable(model)
                ?: fail(
                    MpsErrorCode.MODEL_READ_ONLY,
                    "operation $index destination model is not editable: ${model.name.longName}",
                )
            if (editableModel !in affectedModels) {
                if (editableModel.isChanged) {
                    fail(
                        MpsErrorCode.MODEL_CHANGED,
                        "operation $index destination model has unsaved changes: ${editableModel.name.longName}",
                    )
                }
                affectedModels.add(editableModel)
            }
            return model
        }

        fun resolveConceptOrFail(index: Int, fqn: String): SConcept {
            val concept = ConceptRegistry.getInstance().getConceptByName(fqn)
            if (!concept.isValid) {
                fail(
                    MpsErrorCode.CONCEPT_NOT_FOUND,
                    "operation $index: ${ConceptValidityGuard.messageForUnresolvedConcept(fqn)}",
                )
            }
            return concept as? SConcept
                ?: fail(MpsErrorCode.MODEL_EDIT_FAILED, "operation $index concept is not instantiable: $fqn")
        }

        fun resolveContainmentOrFail(index: Int, parent: SNode, role: String): SContainmentLink {
            val links = parent.concept.containmentLinks.filter { it.name == role }
            return when (links.size) {
                1 -> links.single()
                0 -> fail(
                    MpsErrorCode.ROLE_NOT_FOUND,
                    "operation $index containment role not found: $role on ${parent.concept.qualifiedName}",
                )
                else -> fail(
                    MpsErrorCode.AMBIGUOUS_ROLE,
                    "operation $index ambiguous containment role $role on ${parent.concept.qualifiedName}",
                )
            }
        }

        fun resolveReferenceLinkOrFail(index: Int, node: SNode, role: String): SReferenceLink {
            val links = node.concept.referenceLinks.filter { it.name == role }
            return when (links.size) {
                1 -> links.single()
                0 -> fail(
                    MpsErrorCode.ROLE_NOT_FOUND,
                    "operation $index reference role not found: $role on ${node.concept.qualifiedName}",
                )
                else -> fail(
                    MpsErrorCode.AMBIGUOUS_ROLE,
                    "operation $index ambiguous reference role $role on ${node.concept.qualifiedName}",
                )
            }
        }

        fun resolvePropertyOrFail(index: Int, node: SNode, name: String): SProperty =
            when (val resolution = resolveProperty(node, name)) {
                is PropertyResolution.Found -> resolution.property
                PropertyResolution.Missing -> fail(
                    MpsErrorCode.PROPERTY_NOT_FOUND,
                    "operation $index property not found: $name on ${node.concept.qualifiedName}",
                )
                is PropertyResolution.Ambiguous -> fail(
                    MpsErrorCode.AMBIGUOUS_PROPERTY,
                    "operation $index ambiguous property $name on ${node.concept.qualifiedName}: " +
                        resolution.properties.joinToString { it.name },
                )
            }

        fun resolveReferenceTargetOrFail(index: Int, ownerModel: SModel, reference: MpsNodeReferenceJson): SNode {
            val targetModel = reference.target.model
            val nodeId = reference.target.node
                ?: fail(
                    MpsErrorCode.TARGET_RESOLUTION_FAILED,
                    "operation $index reference ${reference.role} is missing a target node id",
                )
            val resolved = if (targetModel == null) {
                runCatching { ownerModel.getNode(persistence.createNodeId(nodeId)) }.getOrNull()
            } else {
                modelNodeResolver.findNode(project, NodeTarget.NodeReference("$targetModel/$nodeId"))
            }
            return resolved
                ?: fail(
                    MpsErrorCode.TARGET_RESOLUTION_FAILED,
                    "operation $index reference target not found: ${targetModel ?: ownerModel.name.value}/$nodeId",
                )
        }

        fun attach(index: Int, parent: SNode, link: SContainmentLink, child: SNode, position: ChildPosition) {
            val existing = parent.getChildren(link).toList()
            when (position) {
                ChildPosition.Last -> parent.addChild(link, child)
                ChildPosition.First ->
                    existing.firstOrNull()?.let { parent.insertChildBefore(link, child, it) }
                        ?: parent.addChild(link, child)
                is ChildPosition.Index ->
                    existing.getOrNull(position.index)?.let { parent.insertChildBefore(link, child, it) }
                        ?: parent.addChild(link, child)
                ChildPosition.Only -> fail(
                    MpsErrorCode.INVALID_REQUEST,
                    "operation $index position 'only' addresses an existing child and cannot place a new node",
                )
            }
        }

        // Sets properties and references on [node] and builds its nested children, recursively.
        fun populate(
            index: Int,
            model: SModel,
            node: SNode,
            properties: List<MpsNodePropertyJson>?,
            references: List<MpsNodeReferenceJson>?,
            children: List<MpsNodeJson>?,
        ) {
            properties?.forEach { property ->
                SNodeAccessUtil.setPropertyValue(node, resolvePropertyOrFail(index, node, property.name), property.value)
            }
            references?.forEach { reference ->
                val link = resolveReferenceLinkOrFail(index, node, reference.role)
                val target = resolveReferenceTargetOrFail(index, model, reference)
                node.setReferenceTarget(link, target)
                referencePlacements.add(ReferencePlacement(index, node, link, target))
            }
            children?.forEach { childSpec ->
                val role = childSpec.role
                    ?: fail(MpsErrorCode.INVALID_REQUEST, "operation $index inline child is missing a role")
                val link = resolveContainmentOrFail(index, node, role)
                val childNode = model.createNode(resolveConceptOrFail(index, childSpec.concept))
                node.addChild(link, childNode)
                placements.add(Placement(index, node, link, childNode))
                populate(index, model, childNode, childSpec.properties, childSpec.references, childSpec.children)
            }
        }

        for ((index, operation) in batch.operations.withIndex()) {
            when (operation) {
                is EditOperation.SetProperty -> {
                    val node = requireEditableNode(index, operation.target)
                    val property = resolvePropertyOrFail(index, node, operation.name)
                    try {
                        SNodeAccessUtil.setPropertyValue(node, property, operation.value)
                        mutated = true
                    } catch (exception: Exception) {
                        fail(
                            MpsErrorCode.MODEL_EDIT_FAILED,
                            "operation $index failed: ${exception.message ?: exception.javaClass.name}",
                        )
                    }
                }

                is EditOperation.Delete -> {
                    val node = requireEditableNode(index, operation.target)
                    mutated = true
                    node.delete()
                }

                is EditOperation.DeleteChild -> {
                    val node = requireEditableNode(index, operation.target)
                    val child = locateChild(node, operation.role, operation.position)
                        ?: fail(
                            MpsErrorCode.NODE_NOT_FOUND,
                            "operation $index child not found: role ${operation.role}, position ${operation.position}",
                        )
                    mutated = true
                    child.delete()
                }

                is EditOperation.AddChild -> {
                    val node = requireEditableNode(index, operation.target)
                    val link = resolveContainmentOrFail(index, node, operation.role)
                    val concept = resolveConceptOrFail(index, operation.concept)
                    val model = node.model!!
                    mutated = true
                    val child = model.createNode(concept)
                    attach(index, node, link, child, operation.position)
                    placements.add(Placement(index, node, link, child))
                    populate(index, model, child, operation.properties, operation.references, operation.children)
                    bindAlias(index, operation.alias, child)
                }

                is EditOperation.MoveAsChild -> {
                    val node = requireEditableNode(index, operation.target)
                    val newParent = requireEditableNode(index, operation.into)
                    var ancestor: SNode? = newParent
                    while (ancestor != null) {
                        if (ancestor == node) {
                            fail(
                                MpsErrorCode.INVALID_REQUEST,
                                "operation $index cannot move a node into itself or its own descendant",
                            )
                        }
                        ancestor = ancestor.parent
                    }
                    val link = resolveContainmentOrFail(index, newParent, operation.role)
                    mutated = true
                    val oldParent = node.parent
                    if (oldParent != null) {
                        oldParent.removeChild(node)
                    } else {
                        node.model?.removeRootNode(node)
                    }
                    attach(index, newParent, link, node, operation.position)
                    placements.add(Placement(index, newParent, link, node))
                }

                is EditOperation.SetReference -> {
                    val node = requireEditableNode(index, operation.target)
                    val link = resolveReferenceLinkOrFail(index, node, operation.role)
                    val to = operation.to?.let { resolveNode(index, it) }
                    mutated = true
                    node.setReferenceTarget(link, to)
                    // Clearing a reference (to == null) cannot violate a scope; only a concrete target is checked.
                    if (to != null) {
                        referencePlacements.add(ReferencePlacement(index, node, link, to))
                    }
                }

                is EditOperation.CopyAsChild -> {
                    val node = requireEditableNode(index, operation.target)
                    val link = resolveContainmentOrFail(index, node, operation.role)
                    val source = resolveNode(index, operation.source)
                    mutated = true
                    val copy = CopyUtil.copy(source)
                    attach(index, node, link, copy, operation.position)
                    placements.add(Placement(index, node, link, copy))
                    bindAlias(index, operation.alias, copy)
                }

                is EditOperation.AddRoot -> {
                    val model = requireEditableModel(index, operation.model)
                    val concept = resolveConceptOrFail(index, operation.concept)
                    mutated = true
                    val root = model.createNode(concept)
                    model.addRootNode(root)
                    rootPlacements.add(RootPlacement(index, model, root))
                    populate(index, model, root, operation.properties, operation.references, operation.children)
                    bindAlias(index, operation.alias, root)
                }

                is EditOperation.CopyAsRoot -> {
                    val model = requireEditableModel(index, operation.model)
                    val source = resolveNode(index, operation.source)
                    mutated = true
                    val copy = CopyUtil.copy(source)
                    model.addRootNode(copy)
                    rootPlacements.add(RootPlacement(index, model, copy))
                    bindAlias(index, operation.alias, copy)
                }

                is EditOperation.MoveAsRoot -> {
                    val node = requireEditableNode(index, operation.target)
                    val model = requireEditableModel(index, operation.model)
                    mutated = true
                    val oldParent = node.parent
                    if (oldParent != null) {
                        oldParent.removeChild(node)
                    } else {
                        node.model?.removeRootNode(node)
                    }
                    model.addRootNode(node)
                    rootPlacements.add(RootPlacement(index, model, node))
                }
            }
        }

        val violations = evaluateConstraints(placements, rootPlacements, referencePlacements, uncheckable)

        // Strict enforcement refuses to apply anything it could not fully check.
        if (enforcement == ConstraintEnforcement.STRICT && !uncheckable.isEmpty) {
            fail(MpsErrorCode.LANGUAGE_NOT_LOADED, uncheckable.strictFailureMessage())
        }

        val warnings = uncheckable.warnings()

        // Advisory applies and saves despite violations; best-effort and strict block on any violation.
        if (violations.isNotEmpty() && enforcement != ConstraintEnforcement.ADVISORY) {
            // Block: revert the in-memory batch and report the violations without saving.
            if (mutated) {
                reload(affectedModels)
            }
            return ModelEditResponse(created = emptyMap(), violations = violations, warnings = warnings)
        }

        return when (val saveOutcome = writeScope.saveWithResolveInfo(affectedModels)) {
            SaveOutcome.Saved -> ModelEditResponse(
                created = aliases.mapValues { (_, node) -> persistence.asString(node.reference) },
                violations = violations,
                warnings = warnings,
            )
            is SaveOutcome.SaveFailed -> fail(
                MpsErrorCode.SAVE_FAILED,
                "model save failed for ${saveOutcome.model.name.longName}: ${saveOutcome.result}",
            )
        }
    }

    private fun locateChild(
        node: SNode,
        role: String,
        position: ChildPosition,
    ): SNode? {
        val childrenInRole = node.children.filter { it.containmentLink?.name == role }

        return when (position) {
            is ChildPosition.First -> childrenInRole.firstOrNull()
            is ChildPosition.Last -> childrenInRole.lastOrNull()
            is ChildPosition.Only -> childrenInRole.singleOrNull()
            is ChildPosition.Index -> childrenInRole.elementAtOrNull(position.index)
        }
    }

    private fun resolveTarget(project: Project, target: EditTarget): SNode? =
        when (target) {
            is EditTarget.Alias -> error("alias targets must be rejected before resolution")
            is EditTarget.InModel -> modelNodeResolver.findNode(
                project,
                NodeTarget.InModel(modelTarget = target.modelTarget, nodeId = target.nodeId),
            )

            is EditTarget.NodeReference -> modelNodeResolver.findNode(
                project,
                NodeTarget.NodeReference(target.nodeReference),
            )
        }

    /**
     * The name of a batch-local alias, without its reference sigil. A single leading '$' — required when referencing an
     * alias, and how a target string is recognized as an alias rather than a node reference — is not part of the name,
     * so "$root" and "root" denote the same alias whether written in "as" or in a reference.
     */
    private fun aliasName(alias: String): String = alias.removePrefix("$")

    private fun resolveProperty(node: SNode, name: String): PropertyResolution {
        val properties = node.concept.properties.filter { it.name == name }.toList()
        return when (properties.size) {
            0 -> PropertyResolution.Missing
            1 -> PropertyResolution.Found(properties.single())
            else -> PropertyResolution.Ambiguous(properties)
        }
    }

    /**
     * Evaluates the Constraints a batch would violate, using only the cheap constraints/structural APIs (never the
     * typesystem or full Model Check). Covers the structural allowed-child concept, language-defined
     * can-be-child/parent/ancestor rules, containment cardinality, and language-defined reference **scope** rules.
     * Property-value Constraints are a later addition.
     */
    private fun evaluateConstraints(
        placements: List<Placement>,
        rootPlacements: List<RootPlacement>,
        referencePlacements: List<ReferencePlacement>,
        uncheckable: UncheckableLanguages,
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
            // reference as unable-to-check (a language defect) rather than fabricating a scope violation.
            if (scope is ErrorScope) {
                uncheckable.add(sourceConcept)
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

    private fun reload(models: Iterable<EditableSModel>) {
        models.forEach { it.reloadFromSource() }
    }
}

private class Placement(
    val opIndex: Int,
    val parent: SNode,
    val link: SContainmentLink,
    val child: SNode,
)

/**
 * A node placed into Root Node position of [model] by a root Edit Operation, checked against the can-be-root Constraint.
 */
private class RootPlacement(
    val opIndex: Int,
    val model: SModel,
    val node: SNode,
)

/**
 * A concrete reference target set on [node] via [link] by an Edit Operation, checked against the language-defined
 * reference-scope Constraint. Cleared references (null target) are not recorded.
 */
private class ReferencePlacement(
    val opIndex: Int,
    val node: SNode,
    val link: SReferenceLink,
    val target: SNode,
)

/**
 * Collects the languages whose constraints an edit batch could not check because they are not loaded, deduplicated so
 * each language is reported once.
 */
private class UncheckableLanguages {
    private val languages = linkedSetOf<String>()

    fun add(concept: SConcept) {
        languages.add(ConceptValidityGuard.languageName(concept))
    }

    val isEmpty: Boolean get() = languages.isEmpty()

    fun strictFailureMessage(): String =
        "constraints could not be checked because these languages are not loaded: " +
            "${languages.joinToString()} — compile them, or rerun with --constraints=best-effort to skip " +
            "the unchecked constraints"

    fun warnings(): List<String> = uncheckableLanguageWarnings(languages.toList())
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

private sealed interface PropertyResolution {
    data class Found(val property: SProperty) : PropertyResolution
    data class Ambiguous(val properties: List<SProperty>) : PropertyResolution
    data object Missing : PropertyResolution
}
