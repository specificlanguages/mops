package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.DaemonRecordStore
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
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

@ResourceLock("system-streams")
class DaemonStatusStopCommandTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `daemon status reads the current project daemon record without mps home`() {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()
        val record = daemonRecord(
            port = 4321,
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
        assertContains(stdout, "running")
        assertContains(stdout, project.pathString)
        assertContains(stdout, "4321")
        assertContains(stdout, mpsHome.pathString)
    }

    @Test
    fun `daemon status all lists every daemon record without project inference`() {
        val daemonHome = tempDir.resolve("daemon-home")

        // Project directories must exist for daemon records
        val dir1 = tempDir.resolve("one")
        dir1.createDirectories()
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

        val dir2 = tempDir.resolve("two")
        dir2.createDirectories()
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
