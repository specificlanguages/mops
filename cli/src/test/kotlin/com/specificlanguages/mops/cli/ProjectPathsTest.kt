package com.specificlanguages.mops.cli

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectPathsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `project inference walks upward to mps directory`() {
        val project = tempDir.mpsProject()
        val nested = project.resolve("a/b/c").createDirectories()

        assertEquals(project, MopsCommand.resolveProjectPath(nested))
        assertThrows<ProjectPathNotFoundException> {
            MopsCommand.resolveProjectPath(tempDir.resolve("outside").createDirectories())
        }
    }

    @Test
    fun `explicit project root overrides working directory inference`() {
        val project = tempDir.mpsProject()
        val outside = tempDir.resolve("outside").createDirectories()
        val command = MopsCommand(workingDirectory = outside).apply {
            projectRoot = project.pathString
        }

        assertEquals(project, command.resolveProjectPath())
    }
}
