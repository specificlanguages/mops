package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.PongResponse
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DaemonControlIntegrationTest {

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `daemon stop removes the current project record`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        val store = DaemonRecordStore.forDaemonHome(daemonHome)

        val pingExitCode = newCommandLine(workingDirectory = project).execute(
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "ping"
        )
        assertEquals(0, pingExitCode)

        val stdout = ByteArrayOutputStream()

        val stopExitCode = newCommandLine(workingDirectory = project).also {
            it.out = PrintWriter(stdout, true)
        }.execute(
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "stop"
        )

        assertEquals(0, stopExitCode)
        assertContains(stdout.toString(), "stopped")
        assertNull(store.read(project))
    }

    @Test
    @ResourceLock("system-streams")
    fun `daemon ping output remains a single JSON response after replacing a stale project daemon record`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val staleMpsHome = tempDir.resolve("stale-mps").createDirectories().toRealPath()
        val javaHome = Path.of(System.getProperty("java.home")).toRealPath()

        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        store.write(
            DaemonRecord(
                port = 9,
                token = "stale-token",
                pid = 999_999L,
                daemonVersion = "0.3.0-SNAPSHOT",
                context = DaemonContext.fromLivePaths(
                    projectPath = project,
                    mpsHome = staleMpsHome,
                    javaHome = javaHome,
                ),
                workspace = store.workspacePath(project),
                startupTime = "2026-05-12T12:02:00Z",
            ),
        )

        var pingExitCode = Int.MIN_VALUE
        val stdout = tapSystemOut {
            pingExitCode = newCommandLine(workingDirectory = project).execute(
                *javaAndMpsHomeArgs(),
                "--daemon-home", daemonHome.pathString,
                "daemon", "ping",
            )
        }

        try {
            assertEquals(0, pingExitCode)

            val response = try {
                GsonCodec.fromJson(stdout, DaemonResponse::class.java)
            } catch (exception: RuntimeException) {
                throw AssertionError(
                    "daemon ping stdout should be parseable as a single JSON response, but was:\n$stdout",
                    exception,
                )
            }

            assertIs<PongResponse>(response)
            assertEquals(project.toRealPath().pathString, response.projectPath)
        } finally {
            val stopOutput = ByteArrayOutputStream()
            newCommandLine(workingDirectory = project).also {
                it.out = PrintWriter(stopOutput, true)
            }.execute(
                "--daemon-home", daemonHome.pathString,
                "daemon", "stop",
            )
        }
    }

}
