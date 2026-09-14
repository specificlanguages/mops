package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildModuleReloadSemanticsTest {
    @Test
    fun `reload runs through the MPS module classloader and saves refreshed module data`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::makeImportedLanguageNameStale) { project, projectPath ->
            val response = executor(project).execute(
                CodeRunRequest("", """
                    def buildProject = project.read {
                        project.model('com.specificlanguages.json.build').rootNodes.first()
                    }
                    def importedLanguage = project.read {
                        def pending = [buildProject]
                        while (!pending.empty) {
                            def current = pending.remove(0)
                            if (current.concept.qualifiedName == 'jetbrains.mps.build.mps.structure.BuildMps_Language') {
                                return current
                            }
                            for (def child = current.firstChild; child != null; child = child.nextSibling) {
                                pending.add(child)
                            }
                        }
                        throw new AssertionError('fixture has no imported build language')
                    }
                    return project.command {
                        assert importedLanguage.name == 'stale.language.name'
                        def result = mops.editing.build.reloadModulesFromDisk(importedLanguage)
                        assert importedLanguage.name == 'com.specificlanguages.json'
                        result
                    }
                """.trimIndent(), "reload-build-modules.groovy"),
            )

            assertContains(response.output.orEmpty(), "\"succeeded\":true")
            assertContains(buildModel(projectPath).readText(), "value=\"com.specificlanguages.json\"")
            assertFalse("stale.language.name" in buildModel(projectPath).readText())
        }
    }

    @Test
    fun `reload requires command access and a BuildProject ancestor`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val outsideCommand = runCatching {
                executor(project).execute(
                    CodeRunRequest("", """
                        def buildProject = project.read {
                            project.model('com.specificlanguages.json.build').rootNodes.first()
                        }
                        mops.editing.build.reloadModulesFromDisk(buildProject)
                    """.trimIndent(), "reload-outside-command.groovy"),
                )
            }.exceptionOrNull()
            assertContains(outsideCommand?.message.orEmpty(), "requires a project.command access block")

            val wrongConcept = runCatching {
                executor(project).execute(
                    CodeRunRequest("", """
                        def concept = project.read {
                            project.model('com.specificlanguages.json.structure').rootNodes.first()
                        }
                        project.command { mops.editing.build.reloadModulesFromDisk(concept) }
                    """.trimIndent(), "reload-wrong-concept.groovy"),
                )
            }.exceptionOrNull()
            assertContains(wrongConcept?.message.orEmpty(), "no BuildProject ancestor")
        }
    }

    @Test
    fun `reported loader errors are returned with their hinted build node`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::breakImportedLanguagePath) { project, _ ->
            val response = executor(project).execute(
                CodeRunRequest("", """
                    def buildProject = project.read {
                        project.model('com.specificlanguages.json.build').rootNodes.first()
                    }
                    project.command { mops.editing.build.reloadModulesFromDisk(buildProject) }
                """.trimIndent(), "reload-missing-module.groovy"),
            )

            val output = response.output.orEmpty()
            assertContains(output, "\"succeeded\":false")
            assertContains(output, "\"kind\":\"error\"")
            assertTrue(Regex("\\\"node\\\":\\\"[^\\\"]+\\\"").containsMatchIn(output), output)
        }
    }

    private fun executor(project: jetbrains.mps.project.MPSProject) =
        CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform)

    private fun makeImportedLanguageNameStale(projectPath: Path) {
        val file = buildModel(projectPath)
        file.writeText(
            file.readText().replace(
                "<property role=\"TrG5h\" value=\"com.specificlanguages.json\" />\n        <property role=\"3LESm3\" value=\"f3f42ddf-d692-4c29-90fb-7360196f01ab\" />",
                "<property role=\"TrG5h\" value=\"stale.language.name\" />\n        <property role=\"3LESm3\" value=\"f3f42ddf-d692-4c29-90fb-7360196f01ab\" />",
            ),
        )
    }

    private fun breakImportedLanguagePath(projectPath: Path) {
        val file = buildModel(projectPath)
        file.writeText(
            file.readText().replaceFirst(
                "<property role=\"2Ry0Am\" value=\"languages\" />",
                "<property role=\"2Ry0Am\" value=\"missing-modules\" />",
            ),
        )
    }

    private fun buildModel(projectPath: Path): Path =
        projectPath.resolve("solutions/com.specificlanguages.json.build/models/com.specificlanguages.json.build.mps")
}
