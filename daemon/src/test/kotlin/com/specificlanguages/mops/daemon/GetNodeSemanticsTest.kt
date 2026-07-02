package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsResult
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetNodeSemanticsTest {

    @Test
    fun `exports node json`() {
        val node = assertOk(getNodeInSharedProject(NodeTarget.InModel(structureModelPath(), JSON_FILE_NODE_ID)))

        assertEquals(STRUCTURE_MODEL_REFERENCE, node.model)
        assertEquals("jetbrains.mps.lang.structure.structure.ConceptDeclaration", node.concept)
        assertEquals(JSON_FILE_NODE_ID, node.id)
        assertEquals("JsonFile", propertyValue(node, "name"))
        val extendsReference = requireNotNull(node.references).single { it.role == "extends" }
        assertEquals(CORE_STRUCTURE_MODEL_REFERENCE, extendsReference.target.model)
        assertNotNull(extendsReference.target.node)
        assertTrue(requireNotNull(node.children).isNotEmpty())
        assertTrue(requireNotNull(node.children).any { it.role == "implements" })
    }

    @Test
    fun `accepts compact regular node id`() {
        val node = assertOk(getNodeInSharedProject(NodeTarget.InModel(structureModelPath(), "1P8oQ4NaXDS")))

        assertEquals(JSON_FILE_NODE_ID, node.id)
        assertEquals("JsonFile", propertyValue(node, "name"))
    }

    @Test
    fun `accepts serialized model reference as model target`() {
        val node = assertOk(getNodeInSharedProject(NodeTarget.InModel(STRUCTURE_MODEL_REFERENCE, JSON_FILE_NODE_ID)))

        assertEquals(STRUCTURE_MODEL_REFERENCE, node.model)
        assertEquals("JsonFile", propertyValue(node, "name"))
    }

    @Test
    fun `matches model target by model name value instead of long name`() {
        val stereotypedReference = "r:11111111-2222-4333-8444-555555555555(com.specificlanguages.json.structure@tests)"

        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val model = project.resolve(STRUCTURE_MODEL_PATH)
                model.resolveSibling("com.specificlanguages.json.structure@tests.mps").writeText(
                    model.readText().replace(STRUCTURE_MODEL_REFERENCE, stereotypedReference),
                )
            },
        ) { mpsAccess, _ ->
            val unstereotyped = assertOk(
                mpsAccess.read { getNode(NodeTarget.InModel("com.specificlanguages.json.structure", JSON_FILE_NODE_ID)) },
            )
            assertEquals(STRUCTURE_MODEL_REFERENCE, unstereotyped.model)

            val stereotyped = assertOk(
                mpsAccess.read {
                    getNode(NodeTarget.InModel("com.specificlanguages.json.structure@tests", JSON_FILE_NODE_ID))
                },
            )
            assertEquals(stereotypedReference, stereotyped.model)
        }
    }

    @Test
    fun `accepts serialized node reference`() {
        val node = assertOk(getNodeInSharedProject(NodeTarget.NodeReference(JSON_FILE_NODE_REFERENCE)))

        assertEquals(JSON_FILE_NODE_ID, node.id)
        assertEquals("JsonFile", propertyValue(node, "name"))
    }

    @Test
    fun `omits parent role from addressed non-root node`() {
        val node = assertOk(getNodeInSharedProject(NodeTarget.InModel(structureModelPath(), "1P8oQ4NaXDT")))

        assertEquals("jetbrains.mps.lang.structure.structure.InterfaceConceptReference", node.concept)
        assertNull(node.role)
    }

    @Test
    fun `fails instead of guessing when model target is ambiguous`() {
        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val model = project.resolve(STRUCTURE_MODEL_PATH)
                model.resolveSibling("duplicate.structure.mps").writeText(
                    model.readText().replace(
                        STRUCTURE_MODEL_REFERENCE,
                        "r:11111111-2222-4333-8444-555555555555(com.specificlanguages.json.structure)",
                    ),
                )
            },
        ) { mpsAccess, _ ->
            val result = mpsAccess.read {
                getNode(NodeTarget.InModel("com.specificlanguages.json.structure", JSON_FILE_NODE_ID))
            }

            val error = assertError(result)
            assertEquals(MpsErrorCode.GENERIC_FAILURE, error.code)
            assertContains(error.message, "ambiguous model target")
        }
    }

    private fun getNodeInSharedProject(target: NodeTarget): MpsResult<MpsNodeJson> =
        SharedMpsEnvironment.sharedMpsAccess.read { getNode(target) }

    private fun structureModelPath(): String =
        SharedMpsEnvironment.sharedProjectPath.resolve(STRUCTURE_MODEL_PATH).pathString

    private companion object {
        const val STRUCTURE_MODEL_REFERENCE = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val CORE_STRUCTURE_MODEL_REFERENCE = "r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)"
        const val JSON_FILE_NODE_ID = "2110045694544566904"
        const val JSON_FILE_NODE_REFERENCE = "$STRUCTURE_MODEL_REFERENCE/$JSON_FILE_NODE_ID"
        const val STRUCTURE_MODEL_PATH = "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
    }
}
