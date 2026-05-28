package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.MpsListEntryJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import java.time.Instant.now
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull
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

    private fun stopDaemons(project: Path, daemonHome: Path) {
        newCommandLine(
            workingDirectory = project,
        ).execute(
            "--daemon-home",
            daemonHome.pathString,
            "--mps-home", requiredProperty("test.mpsHome"),
            "daemon", "stop",
        )

        waitForAllDaemons(daemonHome)
    }

    private fun waitForAllDaemons(daemonHome: Path) {
        val recordStore = DaemonRecordStore.forDaemonHome(daemonHome)
        val deadline = now().plusSeconds(10)
        while (now() < deadline) {
            val anyAlive = recordStore.readAll().any { record ->
                val handle = ProcessHandle.of(record.record.pid).getOrNull()
                handle != null && handle.isAlive
            }

            if (anyAlive) {
                Thread.sleep(100)
            } else {
                return
            }
        }
    }

    private fun requiredProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "missing system property $name" }

    private data class CliResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val output: String
            get() = "CLI output:\n$stdout\nCLI error:\n$stderr"
    }
}
