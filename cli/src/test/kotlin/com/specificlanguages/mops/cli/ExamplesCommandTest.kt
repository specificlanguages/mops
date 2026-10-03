package com.specificlanguages.mops.cli

import org.junit.jupiter.api.Tag
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import com.specificlanguages.mops.cli.explain.ExplainTopics
import com.specificlanguages.mops.cli.examples.ExampleTopics
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("system-streams")
@Tag("smoke")
class ExamplesCommandTest {
    @Test
    fun `index and every topic work outside an MPS project`() {
        val index = tapSystemOut { assertEquals(0, newCommandLine().execute("examples")) }
        assertContains(index, "mops examples editing")
        for (topic in ExampleTopics.topics + "all") {
            val output = tapSystemOut { assertEquals(0, newCommandLine().execute("examples", topic)) }
            assertEquals(ExampleTopics.page(topic), output)
        }
        val error = tapSystemErr { assertEquals(1, newCommandLine().execute("examples", "unknown")) }
        assertContains(error, "editing.java")
    }

    @Test
    fun `bundled reference links resolve`() {
        val index = ExampleTopics.index()
        for (topic in ExampleTopics.topics) assertContains(index, "($topic.md)")
        for (page in listOf(index) + ExampleTopics.topics.map(ExampleTopics::page)) {
            for (link in Regex("\\]\\(([^)]+[.]md)\\)").findAll(page)) {
                val resource = javaClass.getResource("/examples/${link.groupValues[1]}")
                assertTrue(resource != null, "Missing bundled reference: ${link.groupValues[1]}")
            }
        }
    }

    @Test
    fun `documented CLI recipes parse against the current command tree`() {
        var checked = 0
        for (topic in ExampleTopics.topics) {
            val page = ExampleTopics.page(topic)
            val commands = Regex("`(mops [^`]+)`").findAll(page).map { it.groupValues[1] }.toList() +
                Regex("(?m)^mops [^\\n]+").findAll(page).map { it.value }.toList()
            for (command in commands) {
                val firstCommand = command.substringBefore("|").trimEnd(' ', '\\')
                val args = Regex("'[^']*'|\"[^\"]*\"|\\S+").findAll(firstCommand)
                    .map { it.value.removeSurrounding("'").removeSurrounding("\"") }.drop(1).toList()
                newCommandLine().parseArgs(*args.toTypedArray())
                checked++
            }
        }
        assertTrue(checked > 50, "Expected to validate the CLI recipes, checked $checked")
    }

    @Test
    fun `JSON batch recipes decode with the current protocol`() {
        val page = ExampleTopics.page("editing.json")
        val batches = Regex("'([\\{][^\\n]+[\\}])'").findAll(page).map { it.groupValues[1] }.toList()
        assertEquals(3, batches.size)
        val schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(ExplainTopics.editSchema())
        for (batch in batches) {
            assertTrue(ProtocolJson.decodeBatch(batch).operations.isNotEmpty())
            val errors = schema.validate(batch, InputFormat.JSON)
            assertTrue(errors.isEmpty(), "Edit recipe fails schema validation: $errors")
        }
    }
}
