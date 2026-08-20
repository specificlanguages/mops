package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class CodeModeIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `file program and reflected discovery cross the real daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val program = tempDir.resolve("answer.groovy").also {
            it.writeText("return [answer: 6 * 7, project: project.name]")
        }

        try {
            val run = runCommandLine(
                project,
                "--daemon-home", daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "code", "run", program.pathString,
            )
            assertEquals(0, run.exitCode, run.output)
            assertContains(run.stdout, "\"answer\":42")

            val help = runCommandLine(
                project,
                "--daemon-home", daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "code", "help", "mops.read",
            )
            assertEquals(0, help.exitCode, help.output)
            assertContains(help.stdout, "mops.read.list")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }
}
