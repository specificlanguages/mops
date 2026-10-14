package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ModuleLocationsJson
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetModuleSemanticsTest {

    @Test
    fun `reports the descriptor, source directory, class output, and model roots of a source module`() {
        val projectPath = SharedMpsEnvironment.sharedProjectPath.invariantSeparatorsPathString
        val moduleDir = "$projectPath/languages/com.specificlanguages.json"

        val locations = getModuleInSharedProject(JSON_LANGUAGE)

        assertEquals(
            ModuleLocationsJson(
                kind = "language",
                name = JSON_LANGUAGE,
                reference = JSON_LANGUAGE_REFERENCE,
                packaged = false,
                descriptor = "$moduleDir/com.specificlanguages.json.mpl",
                sourceDirectory = moduleDir,
                classesGen = "$moduleDir/classes_gen",
                classPath = listOf("$moduleDir/classes_gen"),
                modelRoots = listOf(moduleDir),
            ),
            locations,
        )
    }

    @Test
    fun `resolves a module by its serialized reference`() {
        val locations = getModuleInSharedProject(JSON_LANGUAGE_REFERENCE)

        assertEquals(JSON_LANGUAGE, locations.name)
    }

    @Test
    fun `reports the jar, sources jar, and source descriptor of a packaged module`() {
        val locations = getModuleInSharedProject(CORE_LANGUAGE)

        assertEquals("language", locations.kind)
        assertEquals(CORE_LANGUAGE, locations.name)
        assertEquals(CORE_LANGUAGE_REFERENCE, locations.reference)
        assertTrue(locations.packaged)
        val descriptor = assertNotNull(locations.descriptor)
        assertTrue(descriptor.endsWith("/$CORE_LANGUAGE.jar!/META-INF/module.xml"), descriptor)
        assertEquals(descriptor.substringBefore("!/"), locations.jar)
        val sourceDescriptor = assertNotNull(locations.sourceDescriptor)
        assertTrue(sourceDescriptor.endsWith("/$CORE_LANGUAGE-src.jar!/module/$CORE_LANGUAGE.mpl"), sourceDescriptor)
        assertEquals(sourceDescriptor.substringBefore("!/"), locations.sourcesJar)
        assertNull(locations.sourceDirectory)
        assertNull(locations.classesGen)
        assertContains(locations.classPath, locations.jar)
        assertTrue(locations.modelRoots.isNotEmpty(), "model roots: $locations")
        locations.modelRoots.forEach { root -> assertTrue(root.startsWith("${locations.sourcesJar}!/module"), root) }
    }

    @Test
    fun `fails with a named error when no module matches`() {
        val exception = assertFailsWith<MpsRequestException> { getModuleInSharedProject("no.such.module.at.all") }

        assertEquals(MpsErrorCode.MODULE_NOT_FOUND, exception.code)
        assertEquals("module not found: no.such.module.at.all", exception.message)
    }

    @Test
    fun `lists the candidate references instead of picking one when a name is ambiguous`() {
        val duplicateReference = "11111111-2222-4333-8444-555555555555(json.sandbox)"
        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                // A second solution named json.sandbox with its own id and no models: same name, different module.
                val original = project.resolve("solutions/json.sandbox/json.sandbox.msd")
                val duplicate = project.resolve("solutions/json.sandbox.copy/json.sandbox.msd")
                duplicate.parent.toFile().mkdirs()
                duplicate.writeText(
                    original.readText().replace("84cf8fb2-5a00-4f48-8747-6b797fc155f2", "11111111-2222-4333-8444-555555555555"),
                )
                val modules = project.resolve(".mps/modules.xml")
                modules.writeText(
                    modules.readText().replace(
                        "    </projectModules>",
                        "      <modulePath path=\"\$PROJECT_DIR\$/solutions/json.sandbox.copy/json.sandbox.msd\" folder=\"\" />\n" +
                            "    </projectModules>",
                    ),
                )
            },
        ) { mpsAccess, _ ->
            val exception = assertFailsWith<MpsRequestException> { mpsAccess.read { getModule("json.sandbox") } }

            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
            assertContains(exception.message, "ambiguous module: json.sandbox")
            assertContains(exception.message, SANDBOX_REFERENCE)
            assertContains(exception.message, duplicateReference)

            // The serialized reference still names exactly one of them.
            assertEquals(duplicateReference, mpsAccess.read { getModule(duplicateReference) }.reference)
        }
    }

    private fun getModuleInSharedProject(target: String): ModuleLocationsJson =
        SharedMpsEnvironment.sharedMpsAccess.read { getModule(target) }

    private companion object {
        const val JSON_LANGUAGE = "com.specificlanguages.json"
        const val JSON_LANGUAGE_REFERENCE = "f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)"
        const val SANDBOX_REFERENCE = "84cf8fb2-5a00-4f48-8747-6b797fc155f2(json.sandbox)"
        const val CORE_LANGUAGE = "jetbrains.mps.lang.core"
        const val CORE_LANGUAGE_REFERENCE = "ceab5195-25ea-4f22-9b92-103b95ca8c0c(jetbrains.mps.lang.core)"
    }
}
