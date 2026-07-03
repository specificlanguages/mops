package com.specificlanguages.mops.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.specificlanguages.mops.protocol.EditNotation
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the two hand-written union fragments (and the generated schema as a whole) against drifting from what the
 * [ProtocolJson] serializer accepts, using the explain EXAMPLE blocks as ground truth: every example must both parse via
 * the real [ProtocolJson] batch serializer and validate against the generated `model-edit.schema.json`.
 */
class EditSchemaGuardTest {
    private val topLevelTopics = listOf("edit", "target", "position", "node-ref")
    private val operationTopics = EditNotation.operationNames.map { "edit.$it" }
    private val exampleTopics = (topLevelTopics + operationTopics).filter { it != "edit" }
    private val sectionHeaders =
        setOf("FIELDS", "SEMANTICS", "EXAMPLE", "SEE ALSO", "SHAPE", "OPERATIONS", "NOTES", "DRILL DOWN", "FORMS")

    private val schema: JsonSchema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(ExplainTopics.editSchema())
    private val mapper = ObjectMapper()

    @Test
    fun `every example parses via the serializer and validates against the generated schema`() {
        for (topic in exampleTopics) {
            val json = exampleJson(pageText(topic))

            ProtocolJson.decodeBatch(json)

            val errors = schema.validate(mapper.readTree(json))
            assertTrue(errors.isEmpty(), "$topic example fails schema validation: $errors")
        }
    }

    private fun exampleJson(page: String): String {
        val body = sectionBody(page, "EXAMPLE")
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
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
}
