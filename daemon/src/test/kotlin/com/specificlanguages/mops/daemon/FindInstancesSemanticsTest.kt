package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.FindInstancesPayload
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindInstancesSemanticsTest {

    @Test
    fun `finds concept instances including subconcepts`() {
        val result = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(ABSTRACT_CONCEPT_DECLARATION, exact = false, limit = DEFAULT_LIMIT)
        }

        val payload = assertOk(result)
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
        val result = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(ABSTRACT_CONCEPT_DECLARATION, exact = true, limit = DEFAULT_LIMIT)
        }

        assertEquals(FindInstancesPayload(limit = DEFAULT_LIMIT, truncated = false, nodes = emptyList()), assertOk(result))
    }

    @Test
    fun `reports an unresolved concept as not found`() {
        val result = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances("com.specificlanguages.json.structure.DoesNotExist", exact = false, limit = DEFAULT_LIMIT)
        }

        assertEquals(
            MpsResult.Error(
                code = MpsErrorCode.CONCEPT_NOT_FOUND,
                message = "concept not found: com.specificlanguages.json.structure.DoesNotExist",
            ),
            result,
        )
    }

    @Test
    fun `truncates results with a low limit`() {
        val result = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 1)
        }

        val payload = assertOk(result)
        assertEquals(1, payload.nodes.size)
        assertTrue(payload.truncated, "more concept declarations exist than the limit: $payload")
    }

    private companion object {
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val ABSTRACT_CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration"
        const val INTERFACE_CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration"
        const val DEFAULT_LIMIT = 100
    }
}
