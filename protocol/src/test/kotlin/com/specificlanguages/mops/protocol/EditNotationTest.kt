package com.specificlanguages.mops.protocol

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class EditNotationTest {
    @Test
    fun `operation names cover the seven edit operations`() {
        assertEquals(
            setOf(
                "setProperty", "setReference", "addChild", "copyNode", "moveNode", "delete", "deleteChild",
                "addRoot", "copyRoot", "moveToRoot",
            ),
            EditNotation.operationNames.toSet(),
        )
    }

    @Test
    fun `serialized field names list a leaf's fields in declaration order`() {
        assertEquals(listOf("target", "name", "value"), EditNotation.serializedFieldNames("setProperty"))
    }

    @Test
    fun `serialized field names honor SerialName for the alias field and exclude the op discriminator`() {
        val addChild = EditNotation.serializedFieldNames("addChild")

        assertContains(addChild, "target")
        assertContains(addChild, "concept")
        assertContains(addChild, "as")
        assertFalse(addChild.contains("op"), "op discriminator must not appear as a field")
        assertFalse(addChild.contains("alias"), "alias field must be reported under its serial name")
    }

    @Test
    fun `serialized field names reject an unknown operation`() {
        assertFailsWith<IllegalArgumentException> { EditNotation.serializedFieldNames("addNode") }
    }
}
