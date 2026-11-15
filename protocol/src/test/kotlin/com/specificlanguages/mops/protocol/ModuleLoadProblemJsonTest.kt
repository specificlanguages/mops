package com.specificlanguages.mops.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleLoadProblemJsonTest {

    private val absent = ModuleLoadProblemJson("dep.absent", ModuleLoadProblemReason.ABSENT)
    private val unbuilt = ModuleLoadProblemJson(
        "dep.unbuilt", ModuleLoadProblemReason.NOT_BUILT, fix = ModuleLoadFix(listOf("dep.unbuilt")),
    )

    private fun broken(vararg causes: ModuleLoadProblemJson) =
        ModuleLoadProblemJson("top", ModuleLoadProblemReason.BROKEN_DEPENDENCIES, causes = causes.toList())

    @Test
    fun `root causes are the leaves, each module listed once`() {
        val problem = broken(broken(unbuilt), unbuilt, absent)

        assertEquals(listOf(unbuilt, absent), problem.rootCauses)
    }

    @Test
    fun `a problem without causes is its own root cause`() {
        assertEquals(listOf(unbuilt), unbuilt.rootCauses)
    }

    @Test
    fun `combining fixes makes every module once`() {
        assertEquals(
            ModuleLoadFix(listOf("a", "b")),
            ModuleLoadFix.combine(listOf(ModuleLoadFix(listOf("a")), ModuleLoadFix(listOf("b", "a")))),
        )
        assertEquals("mops make module a b", ModuleLoadFix(listOf("a", "b")).command)
    }

    @Test
    fun `combining with a missing fix gives no fix`() {
        assertNull(ModuleLoadFix.combine(listOf(ModuleLoadFix(listOf("a")), null)))
    }

    @Test
    fun `combining no fixes gives no fix`() {
        assertNull(ModuleLoadFix.combine(emptyList()))
    }
}
