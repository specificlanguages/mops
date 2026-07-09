package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.ChildPosition
import com.specificlanguages.mops.protocol.EditNotation
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.parallel.ResourceLock
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@ResourceLock("system-streams")
class ExplainCommandTest {
    private val topLevelTopics =
        listOf("edit", "inline-subtree", "target", "position", "node-ref", "name-pattern", "scope")
    private val operationTopics = EditNotation.operationNames.map { "edit.$it" }
    private val allTopics = topLevelTopics + operationTopics

    // Topics whose EXAMPLE is a shell command rather than an edit-batch JSON object; excluded from batch parsing.
    private val nonBatchTopics = setOf("edit", "name-pattern", "scope")
    private val sectionHeaders =
        setOf("FIELDS", "SEMANTICS", "EXAMPLE", "SEE ALSO", "SHAPE", "OPERATIONS", "NOTES", "DRILL DOWN", "FORMS")

    @Test
    fun `explain with no argument lists all top-level topics and exits zero`() {
        val output = runExplain()

        for (topic in topLevelTopics) {
            assertContains(output, topic)
        }
    }

    @Test
    fun `explain edit prints the edit page and exits zero`() {
        val output = runExplain("edit")

        assertContains(output, "SHAPE")
        assertContains(output, "DRILL DOWN")
    }

    @Test
    fun `explain edit copyAsChild prints the operation page and exits zero`() {
        val output = runExplain("edit.copyAsChild")

        assertContains(output, "copyAsChild")
        assertContains(output, "FIELDS")
    }

    @Test
    fun `explain edit --schema prints the generated schema and exits zero`() {
        val output = runExplain("edit", "--schema")

        assertContains(output, "\"\$schema\"")
        assertContains(output, "\"const\": \"addChild\"")
        assertContains(output, "operations")
    }

    @Test
    fun `explain --schema on a non-edit topic exits non-zero`() {
        var exitCode = Int.MIN_VALUE
        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("explain", "target", "--schema")
        }

        assertNotEquals(0, exitCode)
        assertContains(stderr, "--schema")
    }

    @Test
    fun `unknown path exits non-zero with siblings and a did-you-mean`() {
        var exitCode = Int.MIN_VALUE
        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("explain", "edit.addNode")
        }

        assertNotEquals(0, exitCode)
        assertContains(stderr, "did you mean edit.addRoot")
        assertContains(stderr, "edit.setProperty")
        assertContains(stderr, "edit.deleteChild")
    }

    @Test
    fun `every operation has a matching explain page`() {
        for (op in EditNotation.operationNames) {
            assertTrue(resourceExists("edit.$op"), "missing explain/edit.$op.txt")
        }
    }

    @Test
    fun `each operation page FIELDS section names every serialized field`() {
        for (op in EditNotation.operationNames) {
            val fields = sectionBody(pageText("edit.$op"), "FIELDS")
            for (field in EditNotation.serializedFieldNames(op)) {
                assertContains(fields, field, message = "edit.$op FIELDS must mention '$field'")
            }
        }
    }

    @Test
    fun `no explain edit resource lacks a matching operation`() {
        val editOpFiles = resourceDir().listFiles { f ->
            f.name.matches(Regex("edit\\..+\\.txt"))
        }.orEmpty()

        for (file in editOpFiles) {
            val op = file.name.removePrefix("edit.").removeSuffix(".txt")
            assertContains(EditNotation.operationNames, op, message = "orphan resource ${file.name}")
        }
    }

    @Test
    fun `every drill-down pointer resolves to an existing topic`() {
        for (topic in allTopics) {
            val page = pageText(topic)
            val pointers = sectionBody(page, "SEE ALSO").ifBlank { sectionBody(page, "DRILL DOWN") }
            // Prose hints (e.g. a `mops ...` command line) use backticks; only bare pointer lines name topics.
            val pointerLines = pointers.lines().filterNot { it.contains('`') }.joinToString(" ")
            val tokens = pointerLines.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
            assertTrue(tokens.isNotEmpty(), "$topic must name drill-down paths")
            for (token in tokens) {
                assertContains(allTopics, token, message = "$topic points at unknown topic '$token'")
            }
        }
    }

    @Test
    fun `every example block is a parseable batch and together they cover all target and position forms`() {
        val targetForms = mutableSetOf<String>()
        val positionForms = mutableSetOf<String>()

        for (topic in allTopics.filter { it !in nonBatchTopics }) {
            val json = exampleJson(pageText(topic))
            val batch = ProtocolJson.decodeBatch(json)
            for (op in batch.operations) {
                targetsOf(op).forEach { targetForms += it::class.simpleName!! }
                positionOf(op)?.let { positionForms += it::class.simpleName!! }
            }
        }

        assertEquals(setOf("NodeReference", "InModel", "Alias"), targetForms)
        assertEquals(setOf("First", "Last", "Only", "Index"), positionForms)
    }

    private fun targetsOf(op: EditOperation): List<EditTarget> = when (op) {
        is EditOperation.SetProperty -> listOf(op.target)
        is EditOperation.Delete -> listOf(op.target)
        is EditOperation.DeleteChild -> listOf(op.target)
        is EditOperation.AddChild -> listOf(op.target)
        is EditOperation.SetReference -> listOfNotNull(op.target, op.to)
        is EditOperation.CopyAsChild -> listOf(op.target, op.source)
        is EditOperation.MoveAsChild -> listOf(op.target, op.into)
        is EditOperation.CopyAsRoot -> listOf(op.source)
        is EditOperation.MoveAsRoot -> listOf(op.target)
        is EditOperation.AddRoot -> emptyList()
        is EditOperation.Replace -> listOf(op.target)
    }

    private fun positionOf(op: EditOperation): ChildPosition? = when (op) {
        is EditOperation.AddChild -> op.position
        is EditOperation.CopyAsChild -> op.position
        is EditOperation.MoveAsChild -> op.position
        is EditOperation.DeleteChild -> op.position
        else -> null
    }

    private fun exampleJson(page: String): String {
        val body = sectionBody(page, "EXAMPLE")
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        assertTrue(start in 0 until end, "EXAMPLE must contain a JSON batch")
        return body.substring(start, end + 1)
    }

    private fun sectionBody(page: String, header: String): String {
        val lines = page.lines()
        val start = lines.indexOfFirst { it.trim() == header }
        if (start < 0) return ""
        val body = StringBuilder()
        for (line in lines.drop(start + 1)) {
            if (line.trim() in sectionHeaders) break
            body.appendLine(line)
        }
        return body.toString()
    }

    private fun pageText(topic: String): String =
        checkNotNull(javaClass.getResourceAsStream("/explain/$topic.txt")) { "missing resource for $topic" }
            .bufferedReader().use { it.readText() }

    private fun resourceExists(topic: String): Boolean =
        javaClass.getResource("/explain/$topic.txt") != null

    private fun resourceDir(): File =
        File(checkNotNull(javaClass.getResource("/explain")) { "explain resources missing" }.toURI())

    private fun runExplain(vararg args: String): String {
        var exitCode = Int.MIN_VALUE
        val stdout = tapSystemOut {
            exitCode = newCommandLine().execute("explain", *args)
        }
        assertEquals(0, exitCode)
        return stdout
    }
}
