package com.specificlanguages.mops.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

class InnerClassChangesOuterStateTest {
    private val env = createEnvironment()

    private fun lint(code: String): List<String> =
        InnerClassChangesOuterState(TestConfig()).lintWithContext(env, code).map { it.message }

    @Test
    fun `reports an assignment to an outer var`() {
        val messages = lint(
            """
            class Outer {
                var count = 0

                inner class Pass {
                    fun run() {
                        count = 1
                        this@Outer.count = 2
                        count++
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(3, messages.size, messages.toString())
        assertEquals(
            "Inner class Pass changes count of Outer. Return the change and let Outer apply it.",
            messages.first(),
        )
    }

    @Test
    fun `reports changes to an outer mutable collection`() {
        val messages = lint(
            """
            class Outer {
                val folders = mutableSetOf<String>()
                val sizes = mutableMapOf<String, Int>()

                inner class Pass {
                    fun run(folder: String) {
                        folders += folder
                        folders.add(folder)
                        sizes[folder] = 1
                        sizes.put(folder, 2)
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(4, messages.size, messages.toString())
    }

    @Test
    fun `does not report an inner class that only reads outer state`() {
        val messages = lint(
            """
            class Outer {
                val folders = mutableSetOf<String>()
                var count = 0

                inner class Pass {
                    private val seen = mutableSetOf<String>()
                    private var total = 0

                    fun run(folder: String): Boolean {
                        seen.add(folder)
                        total += count
                        return folder in folders
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(emptyList(), messages)
    }

    @Test
    fun `does not report a nested class that is not inner`() {
        val messages = lint(
            """
            class Outer {
                val folders = mutableSetOf<String>()

                class Pass(private val outer: Outer) {
                    var count = 0

                    fun run(folder: String) {
                        count = 1
                        outer.folders.add(folder)
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(emptyList(), messages)
    }
}
