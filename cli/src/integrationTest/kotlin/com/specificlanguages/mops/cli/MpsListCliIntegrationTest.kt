package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.MpsListEntryJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals

class MpsListCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `lists current project entity through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(project, daemonHome, "--json", "--depth", "0")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("project", root.type)
            assertEquals("mps-json", root.name)
            assertEquals(null, root.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun runList(project: Path, daemonHome: Path, vararg args: String): CliResult {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val exitCode = newCommandLine(
            workingDirectory = project,
        ).also {
            it.out = PrintWriter(stdout, true)
            it.err = PrintWriter(stderr, true)
        }.execute(
            "--daemon-home",
            daemonHome.pathString,
            *javaAndMpsHomeArgs(),
            "list",
            *args,
        )
        return CliResult(
            exitCode = exitCode,
            stdout = stdout.toString(),
            stderr = stderr.toString(),
        )
    }

    private data class CliResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val output: String
            get() = "CLI output:\n$stdout\nCLI error:\n$stderr"
    }
}
