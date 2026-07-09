package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindUsagesSemanticsTest {

    @Test
    fun `finds reference usages of a node reference`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findUsages(NodeTarget.NodeReference(IJSON_VALUE_REFERENCE), limit = DEFAULT_LIMIT)
        }

        assertTrue(payload.usages.isNotEmpty())
        assertTrue(payload.usages.any { it.role == "intfc" }, "expected an implements usage, got: ${payload.usages}")
        assertTrue(
            payload.usages.all { it.owner.reference.startsWith("r:") && it.owner.concept.isNotBlank() },
            "every usage owner should be a real node, got: ${payload.usages}",
        )
    }

    @Test
    fun `finds reference usages of a model target and node id`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findUsages(
                NodeTarget.InModel(modelTarget = "com.specificlanguages.json.structure", nodeId = IJSON_VALUE_NODE_ID),
                limit = DEFAULT_LIMIT,
            )
        }

        assertTrue(payload.usages.isNotEmpty())
        assertTrue(payload.usages.any { it.role == "intfc" }, "expected an implements usage, got: ${payload.usages}")
    }

    @Test
    fun `usage owners carry their immediate parent`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findUsages(NodeTarget.NodeReference(IJSON_VALUE_REFERENCE), limit = DEFAULT_LIMIT)
        }

        val implementsUsage = payload.usages.first { it.role == "intfc" }
        val parent = assertNotNull(implementsUsage.owner.parent)
        assertEquals("root", parent.type)
        assertEquals("implements", parent.role)
        assertEquals("jetbrains.mps.lang.structure.structure.ConceptDeclaration", parent.concept)
        // Find results carry only the immediate parent, never a nested chain.
        assertNull(parent.parent)
    }

    @Test
    fun `in slash searches library models beyond editable project sources`() {
        val editable = SharedMpsEnvironment.sharedMpsAccess.read {
            findUsages(NodeTarget.NodeReference(BASE_CONCEPT_REFERENCE), limit = 0)
        }
        val repository = SharedMpsEnvironment.sharedMpsAccess.read {
            findUsages(NodeTarget.NodeReference(BASE_CONCEPT_REFERENCE), limit = 0, scope = listOf("/"))
        }

        val editableUsages = editable.usages.map { it.role to it.owner.reference }.toSet()
        val repositoryUsages = repository.usages.map { it.role to it.owner.reference }.toSet()

        assertTrue(
            repositoryUsages.size > editableUsages.size,
            "searching the whole repository should reach library usages the editable search excludes",
        )
        assertTrue(
            editableUsages.all { it in repositoryUsages },
            "editable results must remain a subset of the repository results",
        )
    }

    @Test
    fun `scopes a usages search to a module`() {
        val inModule = SharedMpsEnvironment.sharedMpsAccess.read {
            findUsages(NodeTarget.NodeReference(BASE_CONCEPT_REFERENCE), limit = 0, scope = listOf(LANGUAGE_MODULE))
        }
        val inRepository = SharedMpsEnvironment.sharedMpsAccess.read {
            findUsages(NodeTarget.NodeReference(BASE_CONCEPT_REFERENCE), limit = 0, scope = listOf("/"))
        }

        val moduleUsages = inModule.usages.map { it.role to it.owner.reference }.toSet()
        val repositoryUsages = inRepository.usages.map { it.role to it.owner.reference }.toSet()

        assertTrue(moduleUsages.isNotEmpty(), "the language module should hold usages of BaseConcept")
        assertTrue(
            repositoryUsages.size > moduleUsages.size,
            "the repository holds BaseConcept usages beyond the one module",
        )
        assertTrue(
            moduleUsages.all { it in repositoryUsages },
            "module-scoped usages must remain a subset of the repository results",
        )
    }

    private companion object {
        // BaseConcept is extended across the whole platform, so the all-models search finds far more usages than the
        // handful in editable project sources.
        const val BASE_CONCEPT_REFERENCE =
            "r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)/1169194658468"
        const val LANGUAGE_MODULE = "com.specificlanguages.json"
        const val IJSON_VALUE_NODE_ID = "2110045694544566909"
        const val IJSON_VALUE_REFERENCE =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/$IJSON_VALUE_NODE_ID"
        const val DEFAULT_LIMIT = 100
    }
}
