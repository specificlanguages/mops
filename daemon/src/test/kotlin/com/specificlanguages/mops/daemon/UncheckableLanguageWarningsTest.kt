package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertEquals

class UncheckableLanguageWarningsTest {

    @Test
    fun `emits one warning per language below the cap`() {
        val warnings = uncheckableLanguageWarnings(listOf("com.a", "com.b", "com.c"))

        assertEquals(
            listOf(
                "constraints for language 'com.a' were not checked because it is not loaded",
                "constraints for language 'com.b' were not checked because it is not loaded",
                "constraints for language 'com.c' were not checked because it is not loaded",
            ),
            warnings,
        )
    }

    @Test
    fun `caps the detailed warnings at five and summarizes the rest`() {
        val warnings = uncheckableLanguageWarnings((1..8).map { "lang$it" })

        assertEquals(6, warnings.size)
        assertEquals(5, warnings.count { it.startsWith("constraints for language") })
        assertEquals(
            "…and 3 more languages could not be checked because they are not loaded",
            warnings.last(),
        )
    }

    @Test
    fun `missing-language failure names each language with its concepts and the ignore option`() {
        val message = missingLanguagesFailureMessage(
            linkedMapOf("com.a" to setOf("com.a.structure.X", "com.a.structure.Y"), "com.b" to setOf("com.b.structure.Z")),
        )

        assertEquals(
            "constraints could not be checked because these languages are not loaded: " +
                "com.a (com.a.structure.X, com.a.structure.Y), com.b (com.b.structure.Z) — build them " +
                "(e.g. 'mops make module com.a com.b') and retry, or rerun with --ignore-missing-languages to skip " +
                "the unchecked constraints",
            message,
        )
    }

    @Test
    fun `emits nothing when no language was skipped`() {
        assertEquals(emptyList(), uncheckableLanguageWarnings(emptyList()))
    }
}
