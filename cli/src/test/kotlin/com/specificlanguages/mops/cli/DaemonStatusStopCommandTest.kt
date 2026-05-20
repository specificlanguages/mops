package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
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
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            workingDirectory = project,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute(
            "--daemon-home", daemonHome.pathString,
            "daemon", "status"
        )

        assertEquals(0, exitCode)
        val output = stdout.toString()
        assertContains(output, "running")
        assertContains(output, project.pathString)
        assertContains(output, "4321")
        assertContains(output, mpsHome.pathString)
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
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            workingDirectory = tempDir,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute(
            "--daemon-home", daemonHome.pathString,
            "daemon", "status", "--all"
        )

        assertEquals(0, exitCode)
        assertContains(stdout.toString(), dir1.pathString)
        assertContains(stdout.toString(), dir2.pathString)
        assertContains(stdout.toString(), "1111")
        assertContains(stdout.toString(), "2222")
    }

    @Test
    fun `daemon status all lists stale daemon records after context paths disappear`() {
        val daemonHome = tempDir.resolve("daemon-home")
        val staleRecord = writeStaleDaemonRecord(
            daemonHome = daemonHome,
            port = 3333,
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            workingDirectory = tempDir,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute(
            "--daemon-home", daemonHome.pathString,
            "daemon", "status", "--all"
        )

        assertEquals(0, exitCode)
        assertContains(stdout.toString(), staleRecord.projectPath.pathString)
        assertContains(stdout.toString(), staleRecord.mpsHome.pathString)
        assertContains(stdout.toString(), staleRecord.javaHome.pathString)
        assertContains(stdout.toString(), "3333")
    }

    @Test
    fun `daemon stop all removes stale daemon records after context paths disappear`() {
        val daemonHome = tempDir.resolve("daemon-home")
        val staleRecord = writeStaleDaemonRecord(
            daemonHome = daemonHome,
            port = 9,
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = newCommandLine(
            workingDirectory = tempDir,
        ).also {
            it.out = PrintWriter(stdout, true)
        }.execute(
            "--daemon-home", daemonHome.pathString,
            "daemon", "stop", "--all"
        )

        assertEquals(0, exitCode)
        assertContains(stdout.toString(), "removed stale daemon record for project=${staleRecord.projectPath.pathString}")
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
