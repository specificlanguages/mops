package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindNodeByIdSemanticsTest {

    @Test
    fun `finds a node by its decimal id with its full reference`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findNodeById(DECIMAL_ID, limit = DEFAULT_LIMIT)
        }

        assertEquals(
            MpsNodeSummaryJson(
                type = "root",
                name = "JsonFile",
                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                reference = EXPECTED_REFERENCE,
                parent = null,
            ),
            payload.nodes.single(),
        )
        assertFalse(payload.truncated)
    }

    @Test
    fun `finds the same node by its encoded-spelling id`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findNodeById(ENCODED_ID, limit = DEFAULT_LIMIT)
        }

        assertEquals(EXPECTED_REFERENCE, payload.nodes.single().reference)
    }

    // A Node ID is unique only within a model. The fixture holds no cross-model id collision, so the multi-model path
    // (one row per model that holds the id) is exercised by the model-iteration logic rather than a fixture collision.
    @Test
    fun `scopes the lookup to a model and finds nothing in a model that lacks the id`() {
        val inStructureModel = SharedMpsEnvironment.sharedMpsAccess.read {
            findNodeById(DECIMAL_ID, limit = 0, scope = resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL)))
        }
        assertEquals(EXPECTED_REFERENCE, inStructureModel.nodes.single().reference)

        val inBehaviorModel = SharedMpsEnvironment.sharedMpsAccess.read {
            findNodeById(DECIMAL_ID, limit = 0, scope = resolveScope(listOf(LANGUAGE_MODULE, BEHAVIOR_MODEL)))
        }
        assertTrue(inBehaviorModel.nodes.isEmpty(), "the id belongs to the structure model, not behavior: $inBehaviorModel")
    }

    @Test
    fun `in slash searches the whole repository`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findNodeById(DECIMAL_ID, limit = 0, scope = resolveScope(listOf("/")))
        }

        assertEquals(EXPECTED_REFERENCE, payload.nodes.single().reference)
    }

    @Test
    fun `returns an empty success for a well-formed id that matches nothing`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findNodeById(UNUSED_ID, limit = 0, scope = resolveScope(listOf("/")))
        }

        assertTrue(payload.nodes.isEmpty())
        assertFalse(payload.truncated)
    }

    @Test
    fun `fails with a parse error rather than an empty result on a malformed id`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findNodeById("not a node id", limit = 0)
            }
        }

        assertEquals(MpsErrorCode.INVALID_REQUEST, exception.code)
        assertContains(exception.message, "could not parse node id")
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
        const val LANGUAGE_MODULE = "com.specificlanguages.json"
        const val STRUCTURE_MODEL = "com.specificlanguages.json.structure"
        const val BEHAVIOR_MODEL = "com.specificlanguages.json.behavior"
        const val DECIMAL_ID = "2110045694544566904"
        const val ENCODED_ID = "1P8oQ4NaXDS"
        const val EXPECTED_REFERENCE =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        // A well-formed decimal id that no node in the fixture carries.
        const val UNUSED_ID = "1234567890123456789"
    }
}
