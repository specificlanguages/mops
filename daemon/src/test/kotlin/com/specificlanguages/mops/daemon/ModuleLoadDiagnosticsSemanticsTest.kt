package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleLoadDiagnosticsSemanticsTest {

    @Test
    fun `diagnose modules loads a compiled platform language and lists the project's own language`() {
        val response = SharedMpsEnvironment.sharedMpsAccess.read { diagnoseModules() }

        assertTrue(response.summary.total > 0, "the fixture project has diagnosable modules")
        assertTrue(response.summary.loaded > 0, "MPS's own compiled languages load")

        val structure = response.modules.singleOrNull { it.module == STRUCTURE_LANGUAGE }
        assertNotNull(structure, "the structure language should be listed: ${response.modules.map { it.module }}")
        assertTrue(structure.loaded, "the platform structure language loads: $structure")
        assertNull(structure.problem)

        assertTrue(
            response.modules.any { it.module == JSON_LANGUAGE },
            "the project's own language should be listed: ${response.modules.map { it.module }}",
        )
    }

    @Test
    fun `diagnose modules summary and problems are internally consistent`() {
        val response = SharedMpsEnvironment.sharedMpsAccess.read { diagnoseModules() }

        assertEquals(response.modules.size, response.summary.total)
        assertEquals(response.modules.count { it.loaded }, response.summary.loaded)
        assertEquals(response.modules.count { !it.loaded }, response.summary.failed)

        response.modules.forEach { module ->
            assertEquals(module.loaded, module.problem == null, "loaded iff no problem: $module")
            assertTrue(module.present, "listed modules are present: $module")
        }
    }

    @Test
    fun `diagnose module resolves a loaded platform language`() {
        val response = SharedMpsEnvironment.sharedMpsAccess.read { diagnoseModule(STRUCTURE_LANGUAGE) }

        val diagnostic = response.module
        assertEquals(STRUCTURE_LANGUAGE, diagnostic.module)
        assertEquals("language", diagnostic.kind)
        assertTrue(diagnostic.present)
        assertTrue(diagnostic.loaded, "the platform structure language loads: $diagnostic")
        assertNull(diagnostic.problem)
    }

    @Test
    fun `diagnose module reports the source-only fixture language as not built`() {
        val response = SharedMpsEnvironment.sharedMpsAccess.read { diagnoseModule(JSON_LANGUAGE) }

        val diagnostic = response.module
        assertEquals("language", diagnostic.kind)
        assertTrue(diagnostic.present, "the fixture defines this language: $diagnostic")
        assertTrue(!diagnostic.loaded, "the fixture language is not compiled, so it does not load: $diagnostic")
        assertEquals("NOT_BUILT", diagnostic.problem?.reason, "diagnosis: $diagnostic")
    }

    @Test
    fun `diagnose module reports an absent module`() {
        val response = SharedMpsEnvironment.sharedMpsAccess.read { diagnoseModule("no.such.module.at.all") }

        val diagnostic = response.module
        assertTrue(!diagnostic.present)
        assertTrue(!diagnostic.loaded)
        assertEquals("ABSENT", diagnostic.problem?.reason)
    }

    private companion object {
        const val STRUCTURE_LANGUAGE = "jetbrains.mps.lang.structure"
        const val JSON_LANGUAGE = "com.specificlanguages.json"
    }
}
