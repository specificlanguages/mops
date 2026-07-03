package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ChildPosition
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditConstraintViolation
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.NodeTarget
import jetbrains.mps.core.aspects.constraints.rules.kinds.CanBeAncestorContext
import jetbrains.mps.core.aspects.constraints.rules.kinds.ContainmentContext
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.CopyUtil
import jetbrains.mps.smodel.constraints.ConstraintsCanBeFacade
import jetbrains.mps.smodel.language.ConceptRegistry
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
        force: Boolean = false,
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
                return aliases[target.alias]
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
            return node ?: fail(MpsErrorCode.NODE_NOT_FOUND, "operation $index target node not found")
        }

        // Binds a created node to a batch-local alias; a duplicate alias name fails.
        fun bindAlias(index: Int, alias: String?, node: SNode) {
            if (alias == null) {
                return
            }
            if (aliases.putIfAbsent(alias, node) != null) {
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

        fun resolveConceptOrFail(index: Int, fqn: String): SConcept {
            val concept = ConceptRegistry.getInstance().getConceptByName(fqn)
            if (!concept.isValid) {
                fail(MpsErrorCode.CONCEPT_NOT_FOUND, "operation $index concept not found: $fqn")
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
                node.setReferenceTarget(
                    resolveReferenceLinkOrFail(index, node, reference.role),
                    resolveReferenceTargetOrFail(index, model, reference),
                )
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
            val node = requireEditableNode(index, operation.target)

            when (operation) {
                is EditOperation.SetProperty -> {
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
                    mutated = true
                    node.delete()
                }

                is EditOperation.DeleteChild -> {
                    val child = locateChild(node, operation.role, operation.position)
                        ?: fail(
                            MpsErrorCode.NODE_NOT_FOUND,
                            "operation $index child not found: role ${operation.role}, position ${operation.position}",
                        )
                    mutated = true
                    child.delete()
                }

                is EditOperation.AddChild -> {
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

                is EditOperation.MoveNode -> {
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
                    val link = resolveReferenceLinkOrFail(index, node, operation.role)
                    val to = operation.to?.let { resolveNode(index, it) }
                    mutated = true
                    node.setReferenceTarget(link, to)
                }

                is EditOperation.CopyNode -> {
                    val link = resolveContainmentOrFail(index, node, operation.role)
                    val source = resolveNode(index, operation.source)
                    mutated = true
                    val copy = CopyUtil.copy(source)
                    attach(index, node, link, copy, operation.position)
                    placements.add(Placement(index, node, link, copy))
                    bindAlias(index, operation.alias, copy)
                }
            }
        }

        val violations = evaluateConstraints(placements)
        if (violations.isNotEmpty() && !force) {
            // Block: revert the in-memory batch and report the violations without saving.
            if (mutated) {
                reload(affectedModels)
            }
            return ModelEditResponse(created = emptyMap(), violations = violations)
        }

        return when (val saveOutcome = writeScope.saveWithResolveInfo(affectedModels)) {
            SaveOutcome.Saved -> ModelEditResponse(
                created = aliases.mapValues { (_, node) -> persistence.asString(node.reference) },
                violations = violations,
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
     * can-be-child/parent/ancestor rules, and containment cardinality. Reference-scope Constraints are deliberately not
     * evaluated (they run arbitrary language code); property-value Constraints are a later addition.
     */
    private fun evaluateConstraints(placements: List<Placement>): List<EditConstraintViolation> {
        val violations = mutableListOf<EditConstraintViolation>()
        val cardinalityChecked = hashSetOf<String>()

        for (placement in placements) {
            val childConcept = placement.child.concept
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
        return violations
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

private sealed interface PropertyResolution {
    data class Found(val property: SProperty) : PropertyResolution
    data class Ambiguous(val properties: List<SProperty>) : PropertyResolution
    data object Missing : PropertyResolution
}
