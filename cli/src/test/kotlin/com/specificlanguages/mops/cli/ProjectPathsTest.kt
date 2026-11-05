package com.specificlanguages.mops.cli

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `explicit project root without mps directory is reported without an upward search`() {
        val worktree = tempDir.resolve("worktree").createDirectories().toRealPath()

        val error = assertThrows<ProjectPathNotFoundException> { commandWithProjectRoot(worktree).resolveProjectPath() }

        assertEquals("--project-root $worktree has no .mps directory", error.message)
    }

    @Test
    fun `explicit project root without mps directory names projects up to three levels below`() {
        val worktree = tempDir.resolve("worktree").createDirectories().toRealPath()
        val extensions = worktree.resolve("code").mpsProject("extensions")
        worktree.resolve("a/b/c/d").mpsProject("too-deep")

        val error = assertThrows<ProjectPathNotFoundException> { commandWithProjectRoot(worktree).resolveProjectPath() }

        assertEquals(
            "--project-root $worktree has no .mps directory; MPS projects below it, pass one as --project-root: $extensions",
            error.message,
        )
    }

    @Test
    fun `explicit project root without mps directory names at most three projects, nearest first`() {
        val worktree = tempDir.resolve("worktree").createDirectories().toRealPath()
        val near = worktree.mpsProject("near")
        val deeper = listOf("p1", "p2", "p3").map { worktree.resolve("x").mpsProject(it) }

        val error = assertThrows<ProjectPathNotFoundException> { commandWithProjectRoot(worktree).resolveProjectPath() }

        val listed = error.message!!.substringAfter("--project-root: ").split(", ")
        assertEquals(3, listed.size)
        assertEquals(near.toString(), listed.first())
        assertTrue(listed.drop(1).all { it in deeper.map(Path::toString) })
    }

    private fun commandWithProjectRoot(root: Path) =
        MopsCommand(workingDirectory = tempDir.resolve("outside").createDirectories()).apply {
            projectRoot = root.pathString
        }
}
