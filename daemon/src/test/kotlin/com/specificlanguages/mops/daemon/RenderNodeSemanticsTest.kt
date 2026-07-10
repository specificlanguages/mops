package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderNodeSemanticsTest {

    @Test
    fun `renders a node whose language is made through its real editor`() {
        // A ConceptDeclaration is an instance of jetbrains.mps.lang.structure, which is compiled and has an editor, so
        // its own notation is rendered rather than the reflective fallback.
        val text = render(NodeTarget.InModel(STRUCTURE_MODEL_REFERENCE, JSON_FILE_CONCEPT_ID))

        assertContains(text, "concept JsonFile")
        assertContains(text, "extends")
        assertTrue(text.contains('\n'), "expected the rendering to span multiple lines, got: $text")
    }

    @Test
    fun `fails naming the unmade language when a concept in the subtree did not resolve`() {
        // The mps-json fixture ships as pure source, so com.specificlanguages.json is not made and the sandbox's
        // concepts do not resolve. The guard refuses to render and diagnoses the cause through ModuleLoadDiagnostics.
        val exception = assertFailsWith<MpsRequestException> {
            render(NodeTarget.InModel(SANDBOX_MODEL_REFERENCE, SANDBOX_FILE_NODE_ID))
        }

        assertEquals(MpsErrorCode.LANGUAGE_NOT_LOADED, exception.code)
        assertContains(exception.message, "com.specificlanguages.json")
        assertContains(exception.message, "NOT_BUILT")
        assertContains(exception.message, "mops module make")
        assertContains(exception.message, "--allow-reflective")
    }

    @Test
    fun `renders the reflective editor of a root node when reflective is allowed`() {
        // The same unresolved subtree renders under --allow-reflective: the guard is bypassed and MPS falls back to its
        // reflective editor, showing the concept alias and each string role with its value.
        val text = render(NodeTarget.InModel(SANDBOX_MODEL_REFERENCE, SANDBOX_FILE_NODE_ID), allowReflective = true)

        assertContains(text, "json object")
        assertContains(text, "value : foo")
        assertContains(text, "value : x")
    }

    @Test
    fun `renders a non-root node scoped to its own subtree`() {
        val text = render(NodeTarget.InModel(SANDBOX_MODEL_REFERENCE, ARRAY_NODE_ID), allowReflective = true)

        assertContains(text, "json array")
        assertContains(text, "value : 1")
        assertContains(text, "value : x")
        // The array subtree renders alone: the sibling object's "value" string is not part of it.
        assertFalse(text.contains("value : value"), "expected only the array subtree, got: $text")
    }

    @Test
    fun `fails when the node does not resolve`() {
        val exception = assertFailsWith<MpsRequestException> {
            render(NodeTarget.InModel(SANDBOX_MODEL_REFERENCE, "9999999999999999999"), allowReflective = true)
        }
        assertEquals(MpsErrorCode.NODE_NOT_FOUND, exception.code)
    }

    private fun render(target: NodeTarget, allowReflective: Boolean = false): String =
        SharedMpsEnvironment.sharedMpsAccess.extra { renderNode(target, allowReflective) }

    private companion object {
        const val STRUCTURE_MODEL_REFERENCE =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val JSON_FILE_CONCEPT_ID = "2110045694544566904"
        const val SANDBOX_MODEL_REFERENCE = "r:94e02c28-012c-4f06-a2fd-926432934072(json.sandbox)"
        const val SANDBOX_FILE_NODE_ID = "4Twci\$d7zxq"
        const val ARRAY_NODE_ID = "4Twci\$d7zx\$"
    }
}
