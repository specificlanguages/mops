package com.specificlanguages.mops.daemon

import jetbrains.mps.smodel.SNodeId
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StructureMetaIdsTest {

    @Test
    fun `a concept whose node id is another root concept's conceptId gets a random non-negative conceptId`() {
        val (conceptId, peerConceptIds) = inStructureModel { model ->
            val peer = model.getNode(persistence.createNodeId(JSON_OBJECT_ID))!!
            peer.setProperty(peer.property("conceptId"), COLLIDING_ID.toString())
            val concept = model.createNode(peer.concept, SNodeId.Regular(COLLIDING_ID))
            model.addRootNode(concept)

            assign(concept)

            val metaId = concept.property("conceptId")
            concept.getProperty(metaId) to
                model.rootNodes.filter { it != concept }.mapNotNull { it.getProperty(metaId) }.toSet()
        }

        assertNotEquals(COLLIDING_ID.toString(), conceptId)
        assertTrue(conceptId!!.toLong() >= 0)
        assertTrue(conceptId !in peerConceptIds)
    }

    @Test
    fun `a property whose node id is a sibling's propertyId gets a random non-negative propertyId`() {
        val (propertyId, siblingPropertyId) = inStructureModel { model ->
            val sibling = model.getNode(persistence.createNodeId(JSON_STRING_VALUE_PROPERTY_ID))!!
            val metaId = sibling.property("propertyId")
            sibling.setProperty(metaId, COLLIDING_ID.toString())
            val property = model.createNode(sibling.concept, SNodeId.Regular(COLLIDING_ID))
            sibling.parent!!.addChild(sibling.containmentLink!!, property)

            assign(property)

            property.getProperty(metaId) to sibling.getProperty(metaId)
        }

        assertEquals(COLLIDING_ID.toString(), siblingPropertyId)
        assertNotEquals(COLLIDING_ID.toString(), propertyId)
        assertTrue(propertyId!!.toLong() >= 0)
    }

    private fun assign(node: SNode) {
        StructureMetaIds(persistence).apply {
            recordFresh(node)
            assignRecorded()
        }
    }

    private fun SNode.property(name: String) = concept.properties.single { it.name == name }

    private fun <T> inStructureModel(block: (SModel) -> T): T =
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            WriteTransaction().run(project) {
                block(persistence.createModelReference(STRUCTURE_MODEL).resolve(project.repository)!!)
            }
        }

    private companion object {
        val persistence: PersistenceFacade = PersistenceFacade.getInstance()

        const val STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val JSON_OBJECT_ID = "2110045694544567020"

        // JsonStringValue's `value` PropertyDeclaration child.
        const val JSON_STRING_VALUE_PROPERTY_ID = "2110045694544569338"

        // A node id not used in the fixture's structure model.
        const val COLLIDING_ID = 4242L
    }
}
