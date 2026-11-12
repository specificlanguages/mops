package com.specificlanguages.mops.cli

import com.specificlanguages.mops.cli.check.CheckOutputFormat
import com.specificlanguages.mops.cli.check.renderCheckResponse
import com.specificlanguages.mops.cli.find.renderText
import com.specificlanguages.mops.cli.list.ListRenderer
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModelCheckSummary
import com.specificlanguages.mops.protocol.MpsNodeParentJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class HyperlinkedTextRowsTest {
    @Test
    fun `list rows keep the hyperlink markup unescaped`() {
        val output = captureStdout {
            ListRenderer(fullConcept = false, hyperlinks = true).renderText(
                MpsListEntryJson(type = "root", name = "TestClass", concept = "a.ClassConcept", reference = REFERENCE),
                indent = 1,
            )
        }

        assertEquals("  root\tTestClass\tClassConcept\t$LINKED_REFERENCE\n", output)
    }

    @Test
    fun `find rows keep the hyperlink markup unescaped for the node and its parent`() {
        val node = MpsNodeSummaryJson(
            type = "node",
            name = "field",
            concept = "a.FieldDeclaration",
            reference = REFERENCE,
            parent = MpsNodeParentJson(type = "root", name = "TestClass", concept = "a.ClassConcept", reference = REFERENCE),
        )

        assertEquals(
            "node\tfield\tFieldDeclaration\t$LINKED_REFERENCE\tparent\tTestClass\tClassConcept\t$LINKED_REFERENCE",
            renderText(node, fullConcept = false, hyperlinks = true),
        )
    }

    @Test
    fun `check rows keep the hyperlink markup unescaped`() {
        val response = ModelCheckResponse(
            summary = ModelCheckSummary(rootsChecked = 1, errors = 1, warnings = 0, infos = 0, limit = 10, truncated = false),
            findings = listOf(
                ModelCheckFindingJson(
                    severity = FindingSeverity.ERROR,
                    message = "broken",
                    node = MpsNodeSummaryJson(type = "root", name = "TestClass", concept = "ClassConcept", reference = REFERENCE),
                ),
            ),
        )

        val output = captureStdout {
            renderCheckResponse(response, CheckOutputFormat.HUMAN, NodeReferenceFormat.SERIALIZED, hyperlinks = true)
        }

        assertEquals("error\tbroken\tTestClass\tClassConcept\t$LINKED_REFERENCE", output.lines().first())
    }

    @Test
    fun `linked text is escaped like any column while the link target keeps the original reference`() {
        val reference = "r:1(a\tb)/2"

        assertEquals(
            "root\tX\tC\t\u001B]8;;http://127.0.0.1:63320/node?ref=r%3A1%28a%09b%29%2F2\u001B\\" +
                "r:1(a\\tb)/2\u001B]8;;\u001B\\\n",
            captureStdout {
                ListRenderer(fullConcept = false, hyperlinks = true).renderText(
                    MpsListEntryJson(type = "root", name = "X", concept = "C", reference = reference),
                    indent = 0,
                )
            },
        )
    }

    private fun captureStdout(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString(Charsets.UTF_8).replace(System.lineSeparator(), "\n")
    }

    private companion object {
        const val REFERENCE = "r:123(example.model)/456"
        const val LINKED_REFERENCE = "\u001B]8;;http://127.0.0.1:63320/node?ref=r%3A123%28example.model%29%2F456\u001B\\" +
            "$REFERENCE\u001B]8;;\u001B\\"
    }
}
