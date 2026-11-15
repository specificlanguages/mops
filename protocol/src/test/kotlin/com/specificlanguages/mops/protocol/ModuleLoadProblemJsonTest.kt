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
    fun `the complete fix combines the fixes of every root cause`() {
        val other = ModuleLoadProblemJson(
            "dep.other", ModuleLoadProblemReason.NOT_BUILT, fix = ModuleLoadFix(listOf("dep.other")),
        )

        assertEquals(ModuleLoadFix(listOf("dep.unbuilt", "dep.other")), broken(unbuilt, other).completeFix)
        assertEquals("mops make module dep.unbuilt dep.other", broken(unbuilt, other).completeFix?.command)
    }

    @Test
    fun `there is no complete fix when a root cause has no fix`() {
        assertNull(broken(unbuilt, absent).completeFix)
    }

    @Test
    fun `combining no fixes gives no fix`() {
        assertNull(ModuleLoadFix.combine(emptyList()))
    }
}
