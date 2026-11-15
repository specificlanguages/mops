package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLoadFix
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemReason
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `unusable short name message names the languages with reasons and their fixes`() {
        val message = ConceptResolver.unusableShortNameMessage(
            "Expression",
            listOf(
                UnusableLanguage(
                    "exprs",
                    LanguageUnusableReason.RUNTIME_UNAVAILABLE,
                    loadProblem = ModuleLoadProblemJson("exprs", ModuleLoadProblemReason.RUNTIME_LOAD_FAILED, "see the log"),
                ),
                UnusableLanguage(
                    "other.lang",
                    LanguageUnusableReason.GENERATION_REQUIRED,
                    fix = ModuleLoadFix(listOf("other.lang")),
                ),
            ),
        )

        assertContains(message, "\"Expression\" cannot be resolved")
        assertContains(message, "  - exprs: runtime is not loaded\n  - exprs: RUNTIME_LOAD_FAILED — see the log\n")
        assertContains(message, "  - other.lang: generated output may not match current sources; fix: mops make module other.lang")
        assertFalse(message.contains("mops make module exprs"), message)
        // A make cannot fix exprs, so no single command makes every language usable.
        assertFalse(message.contains("then retry"), message)
        assertContains(message, "qualified concept name")
    }

    @Test
    fun `unusable languages message closes with one command when every language has a fix`() {
        val message = unusableLanguagesMessage(
            "this model",
            listOf(
                UnusableLanguage(
                    "a",
                    LanguageUnusableReason.RUNTIME_UNAVAILABLE,
                    loadProblem = ModuleLoadProblemJson(
                        "a", ModuleLoadProblemReason.NOT_BUILT, "not built", fix = ModuleLoadFix(listOf("a")),
                    ),
                    fix = ModuleLoadFix(listOf("a")),
                ),
                UnusableLanguage("b", LanguageUnusableReason.GENERATION_REQUIRED, fix = ModuleLoadFix(listOf("b"))),
            ),
        )

        assertContains(message, "  - a: NOT_BUILT — not built; fix: mops make module a")
        assertTrue(message.endsWith("\nrun 'mops make module a b', then retry"), message)
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
    fun `unloaded language message flattens the problem to its root causes with their fixes`() {
        val problem = ModuleLoadProblemJson(
            module = "a.b",
            reason = ModuleLoadProblemReason.BROKEN_DEPENDENCIES,
            detail = "modules it depends on are not loaded",
            causes = listOf(
                ModuleLoadProblemJson("dep.one", ModuleLoadProblemReason.ABSENT, "not present in the repository"),
                ModuleLoadProblemJson(
                    "dep.two", ModuleLoadProblemReason.NOT_BUILT, "classes not built yet",
                    fix = ModuleLoadFix(listOf("dep.two")),
                ),
            ),
        )

        val message = ConceptResolver.unloadedLanguageMessage(ConceptName("a.b", "Foo"), problem)

        assertContains(message, "its language \"a.b\" is not loaded")
        assertContains(message, "  - dep.one: ABSENT — not present in the repository\n")
        assertContains(message, "  - dep.two: NOT_BUILT — classes not built yet; fix: mops make module dep.two")
        assertContains(message, "run 'mops diagnose module a.b' for the full dependency tree")
        // A problem with causes has no fix of its own, so no single command is promised to load a.b.
        assertFalse(message.contains("then retry"), message)
    }

    @Test
    fun `unloaded language message closes with the fix when every root cause has one`() {
        val problem = ModuleLoadProblemJson(
            module = "a.b",
            reason = ModuleLoadProblemReason.NOT_BUILT,
            detail = "the module should have classes but they have not been built yet",
            fix = ModuleLoadFix(listOf("a.b")),
        )

        val message = ConceptResolver.unloadedLanguageMessage(ConceptName("a.b", "Foo"), problem)

        assertContains(message, "run 'mops make module a.b', then retry")
        assertContains(message, "run 'mops diagnose module a.b' for the full dependency tree")
    }
}
