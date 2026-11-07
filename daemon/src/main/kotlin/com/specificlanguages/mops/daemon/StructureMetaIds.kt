package com.specificlanguages.mops.daemon

import jetbrains.mps.smodel.SNodeId
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeUtil
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import kotlin.random.Random

/**
 * Gives materialized structure-language declarations a meta-id (`conceptId`, `datatypeId`, `propertyId`, `linkId`,
 * `memberId`), the way MPS's `ConceptIdSetter` does on paste. A copied declaration always gets a new meta-id; a fresh one
 * gets one only when its meta-id is empty, so an explicitly set value is kept.
 *
 * The new meta-id follows MPS's `ConceptIdHelper`: the declaration's own regular node id as a long, or a random
 * non-negative long while that value is already the meta-id of another declaration in the same scope. The scope of a
 * concept or datatype is the model's root declarations of that kind; the scope of a property, link, or enumeration member
 * is its siblings under the same parent and role.
 *
 * Matching is subconcept-inclusive, so it covers interface concepts and every kind of datatype declaration. Peers are
 * checked right after the operation that materialized the node; a later move in the same batch does not check them again.
 *
 * One instance serves one edit batch: [recordFresh] and [recordCopy] collect the nodes an Edit Operation materializes,
 * [assignRecorded] runs after each operation, and [hints] reports the assignments at the end of the batch.
 */
internal class StructureMetaIds(private val persistence: PersistenceFacade) {
    private val recorded = mutableListOf<Recorded>()
    private val assignments = mutableListOf<Assignment>()

    /** Records [node], created fresh by the current operation. */
    fun recordFresh(node: SNode) {
        recorded.add(Recorded(node, copied = false))
    }

    /** Records every node of [copy], a subtree the current operation copied. */
    fun recordCopy(copy: SNode) {
        SNodeUtil.getDescendants(copy).mapTo(recorded) { Recorded(it, copied = true) }
    }

    /** Assigns meta-ids to the nodes recorded since the previous call, in the order they were recorded. */
    fun assignRecorded() {
        for ((node, copied) in recorded) {
            val kind = KINDS.firstOrNull { node.concept.isSubConceptOf(it.declaration) } ?: continue
            if (!copied && !node.getProperty(kind.metaId).isNullOrEmpty()) continue
            val id = kind.generate(node).toString()
            node.setProperty(kind.metaId, id)
            assignments.add(Assignment(node, kind.metaId, id))
        }
        recorded.clear()
    }

    /**
     * Returns one hint per assigned meta-id that is still in place: an assignment that a later operation overwrote, or
     * whose node it deleted, is left out. The hint names the node by its current name and reference.
     */
    fun hints(): List<String> =
        assignments
            .filter { (node, metaId, id) -> node.model != null && node.getProperty(metaId) == id }
            .map { (node, metaId, id) ->
                val name = node.name?.let { " $it" }.orEmpty()
                "set ${metaId.name} of ${node.concept.name}$name ${persistence.asString(node.reference)} to $id"
            }

    private data class Recorded(val node: SNode, val copied: Boolean)

    private data class Assignment(val node: SNode, val metaId: SProperty, val id: String)

    private class MetaIdKind(
        val declaration: SConcept,
        val metaId: SProperty,
        private val rootScoped: Boolean,
    ) {
        fun generate(node: SNode): Long {
            val peers = peers(node).filter { it != node }.map { it.getProperty(metaId) }.toSet()
            var id = (node.nodeId as? SNodeId.Regular)?.id ?: randomId()
            while (id.toString() in peers) {
                id = randomId()
            }
            return id
        }

        private fun peers(node: SNode): List<SNode> =
            if (rootScoped) {
                node.model?.rootNodes?.filter { it.concept.isSubConceptOf(declaration) }.orEmpty()
            } else {
                val parent = node.parent ?: return emptyList()
                parent.getChildren(node.containmentLink!!).toList()
            }

        private fun randomId(): Long = Random.nextLong(0, Long.MAX_VALUE)
    }

    private companion object {
        val LANGUAGE_HIGH = 0xc72da2b97cce4447uL.toLong()
        val LANGUAGE_LOW = 0x8389f407dc1158b7uL.toLong()

        fun kind(conceptId: Long, conceptName: String, propertyId: Long, propertyName: String, rootScoped: Boolean) =
            MetaIdKind(
                MetaAdapterFactory.getConcept(
                    LANGUAGE_HIGH,
                    LANGUAGE_LOW,
                    conceptId,
                    "jetbrains.mps.lang.structure.structure.$conceptName",
                ),
                MetaAdapterFactory.getProperty(LANGUAGE_HIGH, LANGUAGE_LOW, conceptId, propertyId, propertyName),
                rootScoped,
            )

        val KINDS = listOf(
            kind(0x1103553c5ffL, "AbstractConceptDeclaration", 0x5d2e6079771f8cc0L, "conceptId", rootScoped = true),
            kind(0xfc26875dfaL, "DataTypeDeclaration", 0x6c1f946a87044403L, "datatypeId", rootScoped = true),
            kind(0xf979bd086bL, "PropertyDeclaration", 0x35a81382d82a4d9L, "propertyId", rootScoped = false),
            kind(0xf979bd086aL, "LinkDeclaration", 0x35a81382d82a4e4L, "linkId", rootScoped = false),
            kind(0x2e770ca32c607c60L, "EnumerationMemberDeclaration", 0x13b8f6fdce540e38L, "memberId", rootScoped = false),
        )
    }
}
