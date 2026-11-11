package com.specificlanguages.mops.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class UnexplainedInnerClassTest {
    private fun lint(code: String): List<String> =
        UnexplainedInnerClass(TestConfig()).lint(code).map { "${it.entity.location.source.line}: ${it.message}" }

    @Test
    fun `reports an inner class without a comment`() {
        val findings = lint(
            """
            class Outer {
                var count = 0

                @Suppress("unused")
                private inner class Pass {
                    fun run() {
                        count = 1
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "5: Inner class Pass has no comment that says why it is inner. Make it a nested or top-level class, " +
                    "or add the comment.",
            ),
            findings,
        )
    }

    @Test
    fun `does not report an inner class with a comment above it`() {
        val findings = lint(
            """
            class Outer {
                val folders = mutableSetOf<String>()

                // Inner: reads the folders of this Outer.
                inner class LineComment {
                    fun has(folder: String) = folder in folders
                }

                /** Inner: reads the folders of this Outer. */
                @Suppress("unused")
                inner class KDoc {
                    fun has(folder: String) = folder in folders
                }
            }
            """.trimIndent(),
        )
        assertEquals(emptyList(), findings)
    }

    @Test
    fun `does not report a nested class that is not inner`() {
        val findings = lint(
            """
            class Outer {
                class Pass(private val outer: Outer) {
                    fun run() = outer.toString()
                }
            }
            """.trimIndent(),
        )
        assertEquals(emptyList(), findings)
    }
}
