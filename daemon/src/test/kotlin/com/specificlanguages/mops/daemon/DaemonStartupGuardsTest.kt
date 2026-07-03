package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers the startup guards that refuse an empty or module-less **MPS Project** so the daemon never reaches a serving
 * state for one. The filesystem guard runs without MPS; the module-count guard is checked against an opened project
 * copy through the shared MPS environment.
 */
class DaemonStartupGuardsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `a project directory without modules xml is a typed startup problem`() {
        val project = tempDir.resolve("project")
        project.resolve(".mps").createDirectories()

        val problem = assertNotNull(missingModulesXmlProblem(project))
        assertEquals("EMPTY_PROJECT", problem.code)
        assertContains(problem.message, project.pathString)
    }

    @Test
    fun `a project with modules xml passes the filesystem guard`() {
        val project = tempDir.resolve("project")
        project.resolve(".mps").createDirectories()
        project.resolve(".mps").resolve("modules.xml").writeText("<project version=\"4\" />")

        assertNull(missingModulesXmlProblem(project))
    }

    @Test
    fun `a project that opens with zero project modules is a typed startup problem`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::emptyModulesXml) { project, projectPath ->
            val problem = assertNotNull(projectModulesProblem(project, projectPath))
            assertEquals("EMPTY_PROJECT", problem.code)
            assertContains(problem.message, projectPath.pathString)
        }
    }

    @Test
    fun `a normal project passes the module guard`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            assertNull(projectModulesProblem(project, projectPath))
        }
    }

    private fun emptyModulesXml(project: Path) {
        project.resolve(".mps").resolve("modules.xml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="MPSProject">
    <projectModules />
  </component>
</project>
""",
        )
    }
}
