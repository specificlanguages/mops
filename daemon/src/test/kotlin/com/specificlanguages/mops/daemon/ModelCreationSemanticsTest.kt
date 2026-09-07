package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import kotlin.io.path.exists
import kotlin.test.*

class ModelCreationSemanticsTest {
    @Test
    fun `code mode retains a native module and returns a native model from a command block`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project).execute(CodeRunRequest(
                "", """
                    def owner = project.read { project.module('com.specificlanguages.json') }
                    project.command {
                      def model = owner.createModel('.fromCode')
                      [name: model.name.value, owner: model.module.moduleName, model: model]
                    }
                """.trimIndent(), "model-creation.groovy",
            ))

            assertContains(requireNotNull(response.output), "\"name\":\"com.specificlanguages.json.fromCode\"")
            assertContains(response.output!!, "\"owner\":\"com.specificlanguages.json\"")
            Unit
        }
    }

    @Test
    fun `single-file creation expands relative name saves and reports identity`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val module = project.projectModulesWithGenerators.first { it.moduleName == "com.specificlanguages.json" }
            val response = WriteTransaction().run(project) {
                ModelCreator(project).create(CreateModelRequest("", ".created", module.moduleReference.toString()))
            }

            val report = assertNotNull(response.report)
            assertEquals("com.specificlanguages.json.created", report.modelName)
            assertEquals(ModelPersistence.SINGLE_FILE, report.persistence)
            assertTrue(report.location.endsWith(".mps"), report.location)
            assertTrue(java.nio.file.Path.of(report.location).exists())
            project.modelAccess.computeReadAction {
                assertNotNull(org.jetbrains.mps.openapi.persistence.PersistenceFacade.getInstance()
                    .createModelReference(report.modelReference).resolve(project.repository))
            }
            Unit
        }
    }

    @Test
    fun `file-per-root dry run plans without creating repository state or files`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val module = project.projectModulesWithGenerators.first { it.moduleName == "com.specificlanguages.json" }
            val response = project.modelAccess.computeReadAction {
                ModelCreator(project).create(CreateModelRequest("", ".planned", module.moduleName!!, filePerRoot = true, dryRun = true))
            }

            val plan = assertNotNull(response.plan)
            assertEquals(ModelPersistence.FILE_PER_ROOT, plan.persistence)
            assertFalse(java.nio.file.Path.of(plan.location).exists())
            project.modelAccess.computeReadAction { assertTrue(module.models.none { it.modelName == plan.modelName }) }
            Unit
        }
    }
}
