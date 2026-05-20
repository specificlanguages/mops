package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DaemonPingCommandTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reports missing mps home`() {
        val project = tempDir.mpsProject()
        val stderr = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            workingDirectory = project,
        ).also {
            it.err = PrintWriter(stderr, true)
        }.execute("daemon", "ping")

        assertEquals(1, exitCode)
        assertContains(stderr.toString(), "MPS home is required")
        assertContains(stderr.toString(), "--mps-home")
    }

    @Test
    fun `reports missing project marker`() {
        val stderr = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            workingDirectory = tempDir,
        ).also {
            it.err = PrintWriter(stderr, true)
        }.execute(
            "--mps-home", "/env/mps",
            "--java-home", "/some/java",
            "daemon", "ping"
        )

        assertEquals(1, exitCode)
        assertContains(stderr.toString(), "no .mps directory found")
    }
}
