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
import kotlin.test.assertContains

class ModuleCreationSemanticsTest {
    @Test
    fun `code mode creates native modules and their companion artifacts`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project).execute(
                com.specificlanguages.mops.protocol.CodeRunRequest(
                    "", """
                        project.command {
                          def language = project.createLanguage('example.code', [descriptor: 'code/language/language.mpl', withGenerator: true])
                          def solution = project.createSolution('example.solution', [descriptor: 'code/solution/solution.msd', usagePreset: 'text'])
                          def devkit = project.createDevkit('example.devkit', [descriptor: 'code/devkit/devkit.devkit'])
                          def generator = language.createGenerator('secondary', [standalone: true, descriptor: 'code/generator/secondary.mpst'])
                          [language: language.moduleName, embedded: language.generators*.moduleName, solution: solution.moduleName,
                           devkit: devkit.moduleName, generator: generator.moduleName]
                        }
                    """.trimIndent(),
                    "module-creation.groovy",
                ),
            )

            assertContains(requireNotNull(response.output), "\"language\":\"example.code\"")
            assertContains(response.output!!, "\"embedded\":[\"example.code.generator\",\"example.code.generator1\"]")
            assertContains(response.output!!, "\"solution\":\"example.solution\"")
            assertContains(response.output!!, "\"devkit\":\"example.devkit\"")
            assertContains(response.output!!, "\"generator\":\"example.code.generator1\"")
            assertTrue(projectPath.resolve("code/language/language.mpl").exists())
            assertTrue(projectPath.resolve("code/solution/solution.msd").exists())
            assertTrue(projectPath.resolve("code/devkit/devkit.devkit").exists())
            assertTrue(projectPath.resolve("code/generator/secondary.mpst").exists())
        }
    }

    @Test
    fun `language dry run resolves the conventional literal dotted path without mutation`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val creator = ModuleCreationCliAdapter(ModuleCreator(project))

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
                val creator = ModuleCreator(project)
                ModuleCreationCliAdapter(creator).createGenerator(
                    CreateGeneratorRequest("", language.moduleName!!, "secondary"),
                ).also { creator.persist() }
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
                val creator = ModuleCreator(project)
                ModuleCreationCliAdapter(creator).createLanguage(
                    CreateLanguageRequest("", "example.created", "custom/modules/created.mpl"),
                ).also { creator.persist() }
            }

            assertTrue(projectPath.resolve("custom/modules/created.mpl").exists())
            assertTrue(project.projectModulesWithGenerators.any { it.moduleName == "example.created" })
            assertEquals(projectPath.resolve("custom/modules/created.mpl").toString(), response.report!!.primary.descriptorPath)
        }
    }
}
