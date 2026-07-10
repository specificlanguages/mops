package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConceptResolverFormatTest {

    @Test
    fun `parses the canonical structure form`() {
        val parsed = ConceptName.parse("com.example.structure.MyConcept")
        assertEquals(ConceptName("com.example", "MyConcept"), parsed)
        assertEquals("com.example.structure.MyConcept", parsed?.qualifiedName)
    }

    @Test
    fun `parses a dropped structure infix and rebuilds the canonical name`() {
        val parsed = ConceptName.parse("com.example.MyConcept")
        assertEquals(ConceptName("com.example", "MyConcept"), parsed)
        assertEquals("com.example.structure.MyConcept", parsed?.qualifiedName)
    }

    @Test
    fun `strips only a trailing structure infix when identifying the language`() {
        // The language namespace may itself end in `.structure`; only the final `.structure` before the concept name
        // is the canonical infix.
        assertEquals(
            ConceptName("jetbrains.mps.lang.structure", "ConceptDeclaration"),
            ConceptName.parse("jetbrains.mps.lang.structure.structure.ConceptDeclaration"),
        )
    }

    @Test
    fun `rejects names without a language segment`() {
        assertNull(ConceptName.parse("MyConcept"))
        assertNull(ConceptName.parse("com.example.structure."))
        assertNull(ConceptName.parse(""))
    }

    @Test
    fun `malformed message states the expected form`() {
        assertContains(ConceptResolver.malformedMessage("NotQualified"), "not a well-formed concept name")
        assertContains(ConceptResolver.malformedMessage("NotQualified"), "<language>.structure.<ConceptName>")
    }

    @Test
    fun `unknown language message names the language`() {
        val message = ConceptResolver.unknownLanguageMessage(ConceptName("a.b", "Foo"))
        assertContains(message, "\"a.b\" is not a module known to this project")
    }

    @Test
    fun `loaded but missing message suggests similar names when present`() {
        val none = ConceptResolver.loadedButMissingMessage(ConceptName("a.b", "Foo"), emptyList())
        assertContains(none, "no concept with a similar name")

        val some = ConceptResolver.loadedButMissingMessage(ConceptName("a.b", "Foo"), listOf("Foobar", "Food"))
        assertContains(some, "did you mean: Foobar, Food?")
    }

    @Test
    fun `ambiguous short name message lists the qualified candidates to retry with`() {
        val message = ConceptResolver.ambiguousShortNameMessage(
            "Statement",
            listOf("com.a.structure.Statement", "com.b.structure.Statement"),
        )

        assertContains(message, "\"Statement\" is ambiguous")
        assertContains(message, "names 2 concepts")
        assertContains(message, "Retry with one qualified name")
        assertContains(message, "  com.a.structure.Statement")
        assertContains(message, "  com.b.structure.Statement")
    }

    @Test
    fun `short name not found message suggests similar concepts when present`() {
        val none = ConceptResolver.shortNameNotFoundMessage("Xyzzy", emptyList())
        assertContains(none, "was not found in any loaded language")
        assertContains(none, "no concept has a similar name")

        val some = ConceptResolver.shortNameNotFoundMessage("Statment", listOf("com.a.structure.Statement"))
        assertContains(some, "was not found in any loaded language")
        assertContains(some, "did you mean: com.a.structure.Statement?")
    }

    @Test
    fun `unloaded language message flattens the problem to its root causes`() {
        val problem = ModuleLoadProblemJson(
            module = "a.b",
            reason = "BROKEN_DEPENDENCIES",
            detail = "modules it depends on are not loaded",
            causes = listOf(
                ModuleLoadProblemJson("dep.one", "ABSENT", "not present in the repository"),
                ModuleLoadProblemJson("dep.two", "NOT_BUILT", "classes not built yet"),
            ),
        )

        val message = ConceptResolver.unloadedLanguageMessage(ConceptName("a.b", "Foo"), problem)

        assertContains(message, "its language \"a.b\" is not loaded")
        assertContains(message, "  - dep.one: ABSENT — not present in the repository")
        assertContains(message, "  - dep.two: NOT_BUILT — classes not built yet")
        assertContains(message, "run 'mops diagnose module a.b' for the full dependency tree")
    }
}
