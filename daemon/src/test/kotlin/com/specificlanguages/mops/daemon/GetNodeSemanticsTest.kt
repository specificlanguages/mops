package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceTargetJson
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetNodeSemanticsTest {

    @Test
    fun `exports node json`() {
        val node = getNodeInSharedProject(NodeTarget.InModel(structureModelPath(), JSON_FILE_NODE_ID))

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
    fun `enriches reference targets with the target name and concept`() {
        val node = getNodeInSharedProject(NodeTarget.InModel(structureModelPath(), JSON_FILE_NODE_ID))

        // Cross-model target: extends points at BaseConcept in jetbrains.mps.lang.core.structure.
        val extends = requireNotNull(node.references).single { it.role == "extends" }
        assertEquals(CORE_STRUCTURE_MODEL_REFERENCE, extends.target.model)
        assertEquals("BaseConcept", extends.target.name)
        assertEquals("jetbrains.mps.lang.structure.structure.ConceptDeclaration", extends.target.concept)

        // Same-model target: the content link declaration references IJsonValue in this model, so the model address
        // is omitted while the name and concept are still filled.
        val target = contentLinkTargetReference(node)
        assertNull(target.model)
        assertEquals("IJsonValue", target.name)
        assertEquals("jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration", target.concept)
    }

    @Test
    fun `leaves an unresolvable reference target name and concept null while keeping the address`() {
        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val model = project.resolve(STRUCTURE_MODEL_PATH)
                // Repoint references to IJsonValue at a node id that no longer exists, leaving them dangling. Only the
                // ref sites use node="...", the IJsonValue definition uses id="...", so it stays intact.
                model.writeText(model.readText().replace("""node="1P8oQ4NaXDX"""", """node="1P8oQ4NbZZZ0""""))
            },
        ) { mpsAccess, _ ->
            val node = mpsAccess.read {
                getNode(NodeTarget.InModel("com.specificlanguages.json.structure", JSON_FILE_NODE_ID))
            }
            val target = contentLinkTargetReference(node)
            assertNotNull(target.node)
            assertFalse(target.resolved)
            assertNull(target.name)
            assertNull(target.concept)
        }
    }

    @Test
    fun `accepts compact regular node id`() {
        val node = getNodeInSharedProject(NodeTarget.InModel(structureModelPath(), "1P8oQ4NaXDS"))

        assertEquals(JSON_FILE_NODE_ID, node.id)
        assertEquals("JsonFile", propertyValue(node, "name"))
    }

    @Test
    fun `accepts serialized model reference as model target`() {
        val node = getNodeInSharedProject(NodeTarget.InModel(STRUCTURE_MODEL_REFERENCE, JSON_FILE_NODE_ID))

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
            val unstereotyped = mpsAccess.read {
                getNode(NodeTarget.InModel("com.specificlanguages.json.structure", JSON_FILE_NODE_ID))
            }
            assertEquals(STRUCTURE_MODEL_REFERENCE, unstereotyped.model)

            val stereotyped = mpsAccess.read {
                getNode(NodeTarget.InModel("com.specificlanguages.json.structure@tests", JSON_FILE_NODE_ID))
            }
            assertEquals(stereotypedReference, stereotyped.model)
        }
    }

    @Test
    fun `accepts serialized node reference`() {
        val node = getNodeInSharedProject(NodeTarget.NodeReference(JSON_FILE_NODE_REFERENCE))

        assertEquals(JSON_FILE_NODE_ID, node.id)
        assertEquals("JsonFile", propertyValue(node, "name"))
    }

    @Test
    fun `omits parent role from addressed non-root node`() {
        val node = getNodeInSharedProject(NodeTarget.InModel(structureModelPath(), "1P8oQ4NaXDT"))

        assertEquals("jetbrains.mps.lang.structure.structure.InterfaceConceptReference", node.concept)
        assertNull(node.role)
    }

    @Test
    fun `includes the immediate parent of an addressed node`() {
        val node = getNodeInSharedProject(NodeTarget.InModel(structureModelPath(), "1P8oQ4NaXDT"))

        val parent = assertNotNull(node.parent)
        assertEquals("root", parent.type)
        assertEquals("implements", parent.role)
        assertEquals("JsonFile", parent.name)
        assertEquals("jetbrains.mps.lang.structure.structure.ConceptDeclaration", parent.concept)
        assertEquals(JSON_FILE_NODE_REFERENCE, parent.reference)
        // Without an ancestry request only the immediate parent is carried, so the chain stops here.
        assertNull(parent.parent)
    }

    @Test
    fun `nests the full ancestry chain up to the root when requested`() {
        val node = SharedMpsEnvironment.sharedMpsAccess.read {
            getNode(NodeTarget.InModel(EDITOR_MODEL_REFERENCE, DEEP_STYLE_NODE_ID), ancestry = true)
        }
        assertEquals("jetbrains.mps.lang.editor.structure.PunctuationLeftStyleClassItem", node.concept)

        val parent = assertNotNull(node.parent)
        assertEquals("node", parent.type)
        assertEquals("styleItem", parent.role)
        assertEquals("jetbrains.mps.lang.editor.structure.CellModel_Constant", parent.concept)

        val grandparent = assertNotNull(parent.parent)
        assertEquals("node", grandparent.type)
        assertEquals("childCellModel", grandparent.role)
        assertEquals("jetbrains.mps.lang.editor.structure.CellModel_Collection", grandparent.concept)

        val root = assertNotNull(grandparent.parent)
        assertEquals("root", root.type)
        assertEquals("cellModel", root.role)
        assertEquals("jetbrains.mps.lang.editor.structure.ConceptEditorDeclaration", root.concept)
        assertNull(root.parent)
    }

    @Test
    fun `stops the parent chain at the immediate parent without an ancestry request`() {
        val node = SharedMpsEnvironment.sharedMpsAccess.read {
            getNode(NodeTarget.InModel(EDITOR_MODEL_REFERENCE, DEEP_STYLE_NODE_ID))
        }

        val parent = assertNotNull(node.parent)
        assertEquals("jetbrains.mps.lang.editor.structure.CellModel_Constant", parent.concept)
        assertNull(parent.parent)
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
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.read {
                    getNode(NodeTarget.InModel("com.specificlanguages.json.structure", JSON_FILE_NODE_ID))
                }
            }

            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
            assertContains(exception.message, "ambiguous model target")
        }
    }

    private fun getNodeInSharedProject(target: NodeTarget): MpsNodeJson =
        SharedMpsEnvironment.sharedMpsAccess.read { getNode(target) }

    private fun contentLinkTargetReference(node: MpsNodeJson): MpsNodeReferenceTargetJson {
        val linkDeclaration = requireNotNull(node.children)
            .single { it.concept == "jetbrains.mps.lang.structure.structure.LinkDeclaration" }
        return requireNotNull(linkDeclaration.references).single { it.role == "target" }.target
    }

    private fun structureModelPath(): String =
        SharedMpsEnvironment.sharedProjectPath.resolve(STRUCTURE_MODEL_PATH).pathString

    private companion object {
        const val STRUCTURE_MODEL_REFERENCE = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val CORE_STRUCTURE_MODEL_REFERENCE = "r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)"
        const val JSON_FILE_NODE_ID = "2110045694544566904"
        const val JSON_FILE_NODE_REFERENCE = "$STRUCTURE_MODEL_REFERENCE/$JSON_FILE_NODE_ID"
        const val EDITOR_MODEL_REFERENCE = "r:4984d1ec-a1c9-4ad1-8af7-b206011783d5(com.specificlanguages.json.editor)"
        // A style item three containment levels below its ConceptEditorDeclaration root: style item -> constant cell ->
        // collection cell -> editor declaration.
        const val DEEP_STYLE_NODE_ID = "1P8oQ4NaXEQ"
        const val STRUCTURE_MODEL_PATH = "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
    }
}
