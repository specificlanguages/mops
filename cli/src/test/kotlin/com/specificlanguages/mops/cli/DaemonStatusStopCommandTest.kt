package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.PongResponse
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import java.net.ServerSocket
import java.net.InetAddress
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ResourceLock("system-streams")
class DaemonStatusStopCommandTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `daemon status pings the current project daemon without mps home`() {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()
        val daemon = startPrerecordedDaemon(PongResponse(project.pathString, mpsHome.pathString, "workspace"))
        val record = daemonRecord(
            port = daemon.port,
            project = project,
            mpsHome = mpsHome,
            workspace = daemonHome.resolve("projects/example"),
        )
        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        store.write(record)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = newCommandLine(workingDirectory = project).execute(
                "--daemon-home", daemonHome.pathString,
                "daemon", "status",
            )
        }

        assertEquals(0, exitCode)
        assertTrue(stdout.lineSequence().any { it.startsWith("running ") })
        assertContains(stdout, project.pathString)
        assertContains(stdout, daemon.port.toString())
        daemon.join(5_000)
        assertEquals(1, daemon.requestsReceived.size)
        assertContains(daemon.requestsReceived.single(), "\"type\":\"ping\"")
        assertContains(stdout, mpsHome.pathString)
    }

    @Test
    fun `daemon status rejects a failed authenticated ping without removing the record`() {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val daemon = startPrerecordedDaemon(DaemonErrorResponse("UNAUTHORIZED", "Invalid token", null))
        val record = daemonRecord(project, daemonHome.resolve("projects/example"), daemon.port,
            mpsHome = tempDir.mpsHome())
        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        store.write(record)
        val stdout = tapSystemOut {
            assertEquals(0, newCommandLine(workingDirectory = project).execute(
                "--daemon-home", daemonHome.pathString, "daemon", "status"))
        }
        daemon.join(5_000)
        assertTrue(stdout.lineSequence().any { it.startsWith("unreachable ") }, stdout)
        assertEquals(record, store.read(project)?.record)
    }

    @Test
    @Timeout(10)
    fun `daemon status times out when a listening process does not answer`() {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val record = daemonRecord(project, daemonHome.resolve("projects/example"), server.localPort,
                mpsHome = tempDir.mpsHome())
            val store = DaemonRecordStore.forDaemonHome(daemonHome)
            store.write(record)
            val stdout = tapSystemOut {
                assertEquals(0, newCommandLine(workingDirectory = project).execute(
                    "--daemon-home", daemonHome.pathString, "daemon", "status"))
            }
            assertTrue(stdout.startsWith("unreachable "), stdout)
            assertEquals(record, store.read(project)?.record)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `daemon status uses explicit project root when working directory is elsewhere`(relative: Boolean) {
        val project = tempDir.mpsProject(name = "code")
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()
        val record = daemonRecord(
            port = 4322,
            project = project,
            mpsHome = mpsHome,
            workspace = daemonHome.resolve("projects/example"),
        )
        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        store.write(record)
        val outside = tempDir.toRealPath()
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = newCommandLine(workingDirectory = outside).execute(
                "--daemon-home", daemonHome.pathString,
                "--project-root", (if (relative) outside.relativize(project) else project).pathString,
                "daemon", "status",
            )
        }

        assertEquals(0, exitCode)
        assertTrue(stdout.startsWith("unreachable "), stdout)
        assertEquals(record, store.read(project)?.record)
        assertContains(stdout, project.pathString)
        assertContains(stdout, "4322")
    }

    @Test
    fun `daemon status all lists every daemon record without project inference`() {
        val daemonHome = tempDir.resolve("daemon-home")

        // Project directories must exist for daemon records
        val dir1 = tempDir.resolve("one").createDirectories().toRealPath()
        val mps1 = tempDir.mpsHome(name = "mps-one")

        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        store.write(
            daemonRecord(
                port = 1111,
                token = "one",
                pid = 1,
                project = dir1,
                mpsHome = mps1,
                workspace = daemonHome.resolve("projects/one"),
            ),
        )

        val dir2 = tempDir.resolve("two").createDirectories().toRealPath()
        val mps2 = tempDir.mpsHome(name = "mps-two")

        store.write(
            daemonRecord(
                port = 2222,
                token = "two",
                pid = 2,
                project = dir2,
                mpsHome = mps2,
                workspace = daemonHome.resolve("projects/two"),
                startupTime = "2026-05-12T12:01:00Z",
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = newCommandLine(workingDirectory = tempDir).execute(
                "--daemon-home", daemonHome.pathString,
                "daemon", "status", "--all",
            )
        }

        assertEquals(0, exitCode)
        assertContains(stdout, dir1.pathString)
        assertContains(stdout, dir2.pathString)
        assertContains(stdout, "1111")
        assertContains(stdout, "2222")
    }

    @Test
    fun `daemon status all lists stale daemon records after context paths disappear`() {
        val daemonHome = tempDir.resolve("daemon-home")
        val staleRecord = writeStaleDaemonRecord(
            daemonHome = daemonHome,
            port = 3333,
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = newCommandLine(workingDirectory = tempDir).execute(
                "--daemon-home", daemonHome.pathString,
                "daemon", "status", "--all",
            )
        }

        assertEquals(0, exitCode)
        assertContains(stdout, staleRecord.projectPath.pathString)
        assertContains(stdout, staleRecord.mpsHome.pathString)
        assertContains(stdout, staleRecord.javaHome.pathString)
        assertContains(stdout, "3333")
        assertTrue(stdout.startsWith("unreachable "), stdout)
        assertTrue(staleRecord.recordPath.exists())
    }

    @Test
    fun `daemon stop all removes stale daemon records after context paths disappear`() {
        val daemonHome = tempDir.resolve("daemon-home")
        val staleRecord = writeStaleDaemonRecord(
            daemonHome = daemonHome,
            port = 9,
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = newCommandLine(workingDirectory = tempDir).execute(
                "--daemon-home", daemonHome.pathString,
                "daemon", "stop", "--all",
            )
        }

        assertEquals(0, exitCode)
        assertContains(stdout, "removed stale daemon record for project=${staleRecord.projectPath.pathString}")
        assertFalse(staleRecord.recordPath.exists())
    }

    private fun writeStaleDaemonRecord(
        daemonHome: Path,
        port: Int,
    ): StaleDaemonRecord {
        val projectPath = tempDir.mpsProject("deleted-project-$port")
        val mpsHome = tempDir.mpsHome("deleted-mps-$port")
        val javaHome = Path.of(System.getProperty("java.home")).toRealPath()
        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        store.write(
            daemonRecord(
                port = port,
                token = "stale-token",
                pid = 999_999L,
                project = projectPath,
                mpsHome = mpsHome,
                workspace = daemonHome.resolve("projects/stale"),
                startupTime = "2026-05-12T12:02:00Z",
            ),
        )
        val recordPath = store.recordPath(projectPath)

        deleteRecursively(projectPath)
        deleteRecursively(mpsHome)

        return StaleDaemonRecord(
            recordPath = recordPath,
            projectPath = projectPath,
            mpsHome = mpsHome,
            javaHome = javaHome,
        )
    }

    private fun deleteRecursively(path: Path) {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private data class StaleDaemonRecord(
        val recordPath: Path,
        val projectPath: Path,
        val mpsHome: Path,
        val javaHome: Path,
    )
}
