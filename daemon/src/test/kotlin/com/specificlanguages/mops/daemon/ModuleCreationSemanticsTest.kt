package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CreateLanguageRequest
import com.specificlanguages.mops.protocol.CreateGeneratorRequest
import com.specificlanguages.mops.protocol.GeneratorPersistence
import com.specificlanguages.mops.protocol.ModuleKind
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleCreationSemanticsTest {
    @Test
    fun `language dry run resolves the conventional literal dotted path without mutation`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val creator = ModuleCreator(project)

            val response = project.modelAccess.computeReadAction {
                creator.createLanguage(CreateLanguageRequest("", "example.language", dryRun = true))
            }

            assertEquals(ModuleKind.LANGUAGE, response.plan!!.primary.kind)
            assertEquals("example.language", response.plan!!.primary.moduleName)
            assertEquals(
                projectPath.resolve("languages/example.language/example.language.mpl").toString(),
                response.plan!!.primary.descriptorPath,
            )
            assertFalse(projectPath.resolve("languages/example.language").exists())
        }
    }

    @Test
    fun `embedded generator derives its name and persists in the language descriptor`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val language = project.getProjectModules(jetbrains.mps.smodel.Language::class.java).single()
            val response = project.modelAccess.computeWriteAction {
                ModuleCreator(project).run { createGenerator(
                    CreateGeneratorRequest("", language.moduleName!!, "secondary"),
                ).also { persist() } }
            }

            assertEquals("com.specificlanguages.json.generator", response.report!!.primary.moduleName)
            assertEquals(GeneratorPersistence.EMBEDDED, response.report!!.primary.persistence)
            val aliases = project.modelAccess.computeReadAction { language.generators.map { it.moduleDescriptor.alias } }
            assertTrue("secondary" in aliases, "generator aliases: $aliases")
        }
    }

    @Test
    fun `language creation persists an exact descriptor and project membership`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val response = project.modelAccess.computeWriteAction {
                ModuleCreator(project).run { createLanguage(
                    CreateLanguageRequest("", "example.created", "custom/modules/created.mpl"),
                ).also { persist() } }
            }

            assertTrue(projectPath.resolve("custom/modules/created.mpl").exists())
            assertTrue(project.projectModulesWithGenerators.any { it.moduleName == "example.created" })
            assertEquals(projectPath.resolve("custom/modules/created.mpl").toString(), response.report!!.primary.descriptorPath)
        }
    }
}
