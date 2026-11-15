package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLoadFix
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleLoadFixPolicyTest {

    @Test
    fun `an unbuilt project module is fixed by making it`() {
        assertEquals(
            ModuleLoadFix(listOf("a")),
            ModuleLoadFixPolicy.forProblem(ModuleLoadProblemReason.NOT_BUILT, "a", inProject = true),
        )
    }

    @Test
    fun `an unbuilt module outside the project has no fix`() {
        assertNull(ModuleLoadFixPolicy.forProblem(ModuleLoadProblemReason.NOT_BUILT, "a", inProject = false))
    }

    @Test
    fun `an unbuilt module whose dependency has no fix has no fix`() {
        val absent = ModuleLoadProblemJson("dep", ModuleLoadProblemReason.ABSENT)

        assertNull(
            ModuleLoadFixPolicy.forProblem(ModuleLoadProblemReason.NOT_BUILT, "a", inProject = true, listOf(absent)),
        )
    }

    @Test
    fun `an unbuilt module whose dependencies all have fixes is fixed by making it`() {
        val unbuilt = ModuleLoadProblemJson("dep", ModuleLoadProblemReason.NOT_BUILT, fix = ModuleLoadFix(listOf("dep")))

        assertEquals(
            ModuleLoadFix(listOf("a")),
            ModuleLoadFixPolicy.forProblem(ModuleLoadProblemReason.NOT_BUILT, "a", inProject = true, listOf(unbuilt)),
        )
    }

    @Test
    fun `other reasons have no fix`() {
        ModuleLoadProblemReason.entries.filter { it != ModuleLoadProblemReason.NOT_BUILT }.forEach {
            assertNull(ModuleLoadFixPolicy.forProblem(it, "a", inProject = true), it.name)
        }
    }

    @Test
    fun `stale generation evidence is fixed by a make only when a make clears every model's evidence`() {
        val stale = evidence(currentHash = "1", generationRecord = "record")

        assertEquals(ModuleLoadFix(listOf("l")), ModuleLoadFixPolicy.forGeneration("l", true, listOf(stale)))
        assertNull(ModuleLoadFixPolicy.forGeneration("l", false, listOf(stale)))
        assertNull(ModuleLoadFixPolicy.forGeneration("l", true, listOf(stale, evidence(currentHash = null, "record"))))
        assertNull(ModuleLoadFixPolicy.forGeneration("l", true, listOf(evidence(currentHash = "1", null))))
    }

    private fun evidence(currentHash: String?, generationRecord: String?) = ModelGenerationEvidence(
        model = "m", source = "s", reason = "r", currentHash = currentHash, recordedHash = null,
        generationRecord = generationRecord, currentHashChecked = true, recordedHashChecked = true,
    )
}
