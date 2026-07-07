package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.FindInstancesResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindInstancesSemanticsTest {

    @Test
    fun `finds concept instances including subconcepts`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(ABSTRACT_CONCEPT_DECLARATION, exact = false, limit = DEFAULT_LIMIT)
        }

        assertTrue(payload.nodes.isNotEmpty())
        assertTrue(payload.nodes.all { it.type == "root" }, "concept declarations are roots: ${payload.nodes}")
        assertTrue(
            payload.nodes.all { it.reference.startsWith("r:") },
            "every node reference should be serialized: ${payload.nodes}",
        )
        val concepts = payload.nodes.map { it.concept }.toSet()
        assertContains(concepts, CONCEPT_DECLARATION)
        assertContains(concepts, INTERFACE_CONCEPT_DECLARATION)
    }

    @Test
    fun `exact match excludes subconcept instances`() {
        // The default search in the preceding test finds subconcept instances of this abstract concept;
        // exact matching requires the direct concept, of which the fixture has none.
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(ABSTRACT_CONCEPT_DECLARATION, exact = true, limit = DEFAULT_LIMIT)
        }

        assertEquals(FindInstancesResponse(limit = DEFAULT_LIMIT, truncated = false, nodes = emptyList()), payload)
    }

    @Test
    fun `all searches library models beyond editable project sources`() {
        val editable = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, all = false)
        }
        val all = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, all = true)
        }

        val editableReferences = editable.nodes.map { it.reference }.toSet()
        val allReferences = all.nodes.map { it.reference }.toSet()

        assertTrue(editableReferences.isNotEmpty(), "fixture should hold editable concept declarations")
        assertTrue(
            allReferences.size > editableReferences.size,
            "searching all models should reach library instances the editable search excludes",
        )
        assertTrue(
            editableReferences.all { it in allReferences },
            "editable results must remain a subset of the all-models results",
        )
    }

    @Test
    fun `non-root instances carry their immediate parent`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(LINK_DECLARATION, exact = false, limit = DEFAULT_LIMIT)
        }

        assertTrue(payload.nodes.isNotEmpty(), "fixture should hold link declarations")
        assertTrue(payload.nodes.all { it.type == "node" }, "link declarations are children, not roots: ${payload.nodes}")
        val instance = payload.nodes.first()
        val parent = assertNotNull(instance.parent)
        assertEquals("root", parent.type)
        assertEquals("linkDeclaration", parent.role)
        assertEquals(CONCEPT_DECLARATION, parent.concept)
        // Find results carry only the immediate parent, never a nested chain.
        assertNull(parent.parent)
    }

    @Test
    fun `reports an unresolved concept as not found`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances("com.specificlanguages.json.structure.DoesNotExist", exact = false, limit = DEFAULT_LIMIT)
            }
        }

        assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
        assertEquals(
            "no valid MPS Concept resolved for \"com.specificlanguages.json.structure.DoesNotExist\" " +
                "(probable owning language: com.specificlanguages.json) — either the name is wrong, or its owning " +
                "language is not compiled or not loaded into the project; build the language and retry",
            exception.message,
        )
    }

    @Test
    fun `truncates results with a low limit`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 1)
        }

        assertEquals(1, payload.nodes.size)
        assertTrue(payload.truncated, "more concept declarations exist than the limit: $payload")
    }

    private companion object {
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val ABSTRACT_CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration"
        const val INTERFACE_CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration"
        const val LINK_DECLARATION = "jetbrains.mps.lang.structure.structure.LinkDeclaration"
        const val DEFAULT_LIMIT = 100
    }
}
