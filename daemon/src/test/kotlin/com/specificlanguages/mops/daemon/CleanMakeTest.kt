package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which modules a clean make may clean: only those whose output folders hold no other module's output. */
class CleanMakeTest {
    @Test
    fun `modules with separate output folders can be made clean`() {
        val roots = mapOf(
            "a" to setOf("/p/a/source_gen", "/p/a/source_gen.caches"),
            "b" to setOf("/p/b/source_gen"),
        )

        assertEquals(setOf("a", "b"), modulesWithOwnOutputRoots(listOf("a", "b"), roots))
    }

    @Test
    fun `a module sharing an output folder with another module is not made clean`() {
        val roots = mapOf("a" to setOf("/p/out"), "b" to setOf("/p/out/"), "c" to setOf("/p/c/source_gen"))

        assertEquals(setOf("c"), modulesWithOwnOutputRoots(listOf("a", "c"), roots))
    }

    @Test
    fun `nested output folders count as shared in both directions`() {
        val roots = mapOf("outer" to setOf("/p/gen"), "inner" to setOf("/p/gen/inner"))

        assertEquals(emptySet(), modulesWithOwnOutputRoots(listOf("outer", "inner"), roots))
    }

    @Test
    fun `a common name prefix is not nesting`() {
        val roots = mapOf("a" to setOf("/p/source_gen"), "b" to setOf("/p/source_gen2"))

        assertEquals(setOf("a", "b"), modulesWithOwnOutputRoots(listOf("a", "b"), roots))
    }
}
