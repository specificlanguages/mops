package com.specificlanguages.mops.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EditSchemaTest {
    private val schema: JsonObject = Json.parseToJsonElement(EditSchema.generate()).jsonObject

    private fun opSchemas(): List<JsonObject> =
        schema["properties"]!!.jsonObject["operations"]!!.jsonObject["items"]!!.jsonObject["oneOf"]!!
            .jsonArray.map { it.jsonObject }

    private fun opSchema(op: String): JsonObject =
        opSchemas().first { it["properties"]!!.jsonObject["op"]!!.jsonObject["const"]!!.jsonPrimitive.content == op }

    private fun required(op: JsonObject): List<String> =
        op["required"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `batch is an object requiring an operations array`() {
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertTrue("operations" in schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val operations = schema["properties"]!!.jsonObject["operations"]!!.jsonObject
        assertEquals("array", operations["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `operations items are a oneOf of the ops keyed by an op const`() {
        val consts = opSchemas().map {
            it["properties"]!!.jsonObject["op"]!!.jsonObject["const"]!!.jsonPrimitive.content
        }
        assertEquals(EditNotation.operationNames.toSet(), consts.toSet())
        assertEquals(EditNotation.operationNames.size, consts.size)
    }

    @Test
    fun `each op requires op plus its non-optional fields and omits optional ones`() {
        val setProperty = opSchema("setProperty")
        assertEquals(listOf("op", "target", "name"), required(setProperty))
        assertFalse("value" in required(setProperty), "value is optional")
        assertTrue("value" in setProperty["properties"]!!.jsonObject.keys)
    }

    @Test
    fun `primitive field types follow the descriptor`() {
        val position = opSchema("copyNode")["properties"]!!.jsonObject
        assertEquals("string", position["role"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `EditTarget fields splice in the hand-written union fragment`() {
        val target = opSchema("delete")["properties"]!!.jsonObject["target"]!!.jsonObject
        val branches = target["anyOf"]!!.jsonArray.map { it.jsonObject }
        assertTrue(branches.any { it["type"]?.jsonPrimitive?.contentOrNull == "string" }, "string branch")
        val obj = branches.first { it["type"]?.jsonPrimitive?.contentOrNull == "object" }
        assertEquals(setOf("model", "nodeId"), obj["properties"]!!.jsonObject.keys)
    }

    @Test
    fun `ChildPosition fields accept the enum or a bare integer, never an index object`() {
        val position = opSchema("deleteChild")["properties"]!!.jsonObject["position"]!!.jsonObject
        val branches = position["anyOf"]!!.jsonArray
        val enums = branches.mapNotNull { it.jsonObject["enum"] }.flatMap { it.jsonArray.map { v -> v.jsonPrimitive.content } }
        assertEquals(setOf("first", "last", "only"), enums.toSet())
        assertTrue(branches.any { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "integer" }, "integer branch")
        assertFalse(EditSchema.generate().contains("\"index\""), "ChildPosition must not describe an {index} object")
    }

    @Test
    fun `array fields use array type with item schemas`() {
        val addChild = opSchema("addChild")["properties"]!!.jsonObject
        val properties = unwrapNullable(addChild["properties"]!!.jsonObject)
        assertEquals("array", properties["type"]!!.jsonPrimitive.content)
        assertNotNull(properties["items"])
    }

    // Optional array fields are nullable, so they may be wrapped alongside a null branch.
    private fun unwrapNullable(schema: JsonObject): JsonObject {
        val anyOf = schema["anyOf"]?.jsonArray ?: return schema
        return anyOf.map { it.jsonObject }.first { it["type"]?.jsonPrimitive?.contentOrNull != "null" }
    }
}
