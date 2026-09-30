package com.specificlanguages.mops.daemon

import kotlin.test.*

class CodeMemberInspectorTest {
    interface Ancestor { fun inheritedOperation(): String }
    interface Descendant : Ancestor
    class Dangerous : Descendant {
        val children: List<String> get() = error("getter must not run")
        var enabled: Boolean = false
        override fun inheritedOperation(): String = error("method must not run")
        override fun toString(): String = error("toString must not run")
    }

    @Test
    fun `inspection describes properties and methods without invoking them`() {
        val result = CodeMemberInspector.inspect(Dangerous::class.java)
        assertEquals(
            CodePropertyMember("children", "java.util.List<java.lang.String>", "getChildren", null,
                Dangerous::class.java.name, false),
            result.properties.single { it.name == "children" },
        )
        assertEquals("setEnabled", result.properties.single { it.name == "enabled" }.setter)
        assertTrue(result.methods.any { it.name == "getChildren" })
        assertFalse(result.methods.any { it.name == "wait" || it.name.contains('$') })
    }

    @Test
    fun `hierarchy includes recursive interfaces once`() {
        assertEquals(listOf(Dangerous::class.java, Descendant::class.java, Ancestor::class.java, Any::class.java),
            CodeMemberInspector.typeHierarchy(Dangerous::class.java))
        val inherited = CodeMemberInspector.inspect(Descendant::class.java).methods.single { it.name == "inheritedOperation" }
        assertEquals(CodeMethodMember("inheritedOperation", emptyList(), "java.lang.String",
            Ancestor::class.java.name, true, false), inherited)
    }

    @Test
    fun `native node accessors remain available for extension shadowing`() {
        val result = CodeMemberInspector.inspect(org.jetbrains.mps.openapi.model.SNode::class.java)
        for (name in listOf("children", "properties", "references")) {
            val property = result.properties.single { it.name == name }
            assertEquals("get" + name.replaceFirstChar { it.uppercase() }, property.getter)
            assertTrue(result.methods.any { it.name == property.getter && it.parameters.isEmpty() })
        }
    }

    @Test
    fun `class lookup accepts known simple names and qualified names`() {
        assertEquals(JavaParsingResult::class.java, CodeMemberInspector.resolveType("JavaParsingResult"))
        assertEquals(Dangerous::class.java, CodeMemberInspector.resolveType(Dangerous::class.java.name))
        assertNull(CodeMemberInspector.resolveType("missing.Unknown"))
    }
}
