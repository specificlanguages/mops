package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.PongResponse
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@ResourceLock("system-streams")
class DaemonControlIntegrationTest {

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `daemon stop removes the current project record`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        val store = DaemonRecordStore.forDaemonHome(daemonHome)

        val ping = runCommandLine(
            project,
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "ping"
        )
        assertEquals(0, ping.exitCode, ping.output)

        val daemonPid = store.read(project)?.record?.pid
            ?: throw AssertionError("a running daemon should have published its record")

        val stop = runCommandLine(
            project,
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "stop"
        )

        assertEquals(0, stop.exitCode, stop.output)
        assertContains(stop.stdout, "stopped")
        assertNull(store.read(project))
        // The daemon must actually terminate, not just drop its record: a surviving process would keep holding the
        // MPS workspace lock and block the next start.
        assertFalse(
            ProcessHandle.of(daemonPid).map { it.isAlive }.orElse(false),
            "daemon process pid=$daemonPid should have exited after stop",
        )
    }

    @Test
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

        val ping = runCommandLine(
            project,
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "ping",
        )

        try {
            assertEquals(0, ping.exitCode, ping.output)

            val response = try {
                ProtocolJson.decodeResponse(ping.stdout)
            } catch (exception: RuntimeException) {
                throw AssertionError(
                    "daemon ping stdout should be parseable as a single JSON response, but was:\n${ping.stdout}",
                    exception,
                )
            }

            assertIs<PongResponse>(response)
            assertEquals(project.toRealPath().pathString, response.projectPath)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

}
