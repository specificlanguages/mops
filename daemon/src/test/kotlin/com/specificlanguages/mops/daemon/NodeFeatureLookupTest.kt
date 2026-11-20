package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NodeFeatureLookupTest {
    private fun feature(name: String, owner: String) = NamedNodeFeature("$owner.$name", name, owner)

    private fun find(kind: NodeFeatureKind, name: String, vararg features: NamedNodeFeature<String>): String =
        requireNamedNodeFeature(kind, "a.b.Concept", name, features.toList())

    @Test
    fun `finds the one feature with the name`() {
        assertEquals(
            "a.b.Named.name",
            find(NodeFeatureKind.PROPERTY, "name", feature("other", "a.b.Concept"), feature("name", "a.b.Named")),
        )
    }

    @Test
    fun `a missing feature fails and lists the known names sorted once`() {
        val failure = assertFailsWith<MpsRequestException> {
            find(
                NodeFeatureKind.REFERENCE_ROLE,
                "target",
                feature("zeta", "a.b.Concept"),
                feature("alpha", "a.b.Base"),
                feature("alpha", "a.b.Other"),
            )
        }
        assertEquals(MpsErrorCode.ROLE_NOT_FOUND, failure.code)
        assertEquals("concept a.b.Concept has no reference role 'target'; its reference roles: alpha, zeta", failure.message)
    }

    @Test
    fun `a missing feature on a concept without features says none`() {
        val failure = assertFailsWith<MpsRequestException> { find(NodeFeatureKind.CHILD_ROLE, "body") }
        assertEquals("concept a.b.Concept has no child role 'body'; its child roles: none", failure.message)
    }

    @Test
    fun `a name that several features share fails and names the declaring concepts`() {
        val failure = assertFailsWith<MpsRequestException> {
            find(NodeFeatureKind.PROPERTY, "name", feature("name", "a.b.Second"), feature("name", "a.b.First"))
        }
        assertEquals(MpsErrorCode.AMBIGUOUS_PROPERTY, failure.code)
        assertEquals(
            "concept a.b.Concept has 2 properties named 'name', declared by a.b.First, a.b.Second",
            failure.message,
        )
    }

    @Test
    fun `ambiguous roles use the role error code`() {
        val failure = assertFailsWith<MpsRequestException> {
            find(NodeFeatureKind.CHILD_ROLE, "body", feature("body", "a.b.X"), feature("body", "a.b.Y"))
        }
        assertEquals(MpsErrorCode.AMBIGUOUS_ROLE, failure.code)
    }
}
