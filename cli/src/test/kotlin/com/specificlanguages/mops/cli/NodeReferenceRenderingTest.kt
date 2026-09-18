package com.specificlanguages.mops.cli

import com.specificlanguages.mops.cli.output.renderNodeReference
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeReferenceRenderingTest {
    @Test
    fun `plain presentation preserves the serialized reference`() {
        assertEquals(NODE_REFERENCE, renderNodeReference(NODE_REFERENCE, hyperlinks = false))
    }

    @Test
    fun `terminal presentation links the serialized reference to MPS`() {
        assertEquals(
            "\u001B]8;;http://127.0.0.1:63320/node?ref=" +
                    "r%3A123%28example.model%29%2F456\u001B\\" +
                    "$NODE_REFERENCE\u001B]8;;\u001B\\",
            renderNodeReference(NODE_REFERENCE, hyperlinks = true),
        )
    }

    private companion object {
        const val NODE_REFERENCE = "r:123(example.model)/456"
    }
}
