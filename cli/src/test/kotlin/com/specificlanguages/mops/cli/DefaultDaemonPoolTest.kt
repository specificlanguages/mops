package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DefaultDaemonClient
import com.specificlanguages.mops.daemoncomms.DefaultDaemonPool
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.daemoncomms.DaemonLauncher
import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.PongResponse
import com.specificlanguages.mops.protocol.StoppedResponse
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DefaultDaemonPoolTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `reuses an existing project daemon record`() {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()
        val javaHome = Path.of(System.getProperty("java.home")).toRealPath()

        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        val fakeDaemon = startPrerecordedDaemon(
            PongResponse(
                projectPath = project.pathString,
                mpsHome = mpsHome.pathString,
                workspacePath = "/project"
            ),
        )
        val daemonRecord = daemonRecord(
            port = fakeDaemon.port,
            project = project,
            mpsHome = mpsHome,
            workspace = Path.of("/project"),
        )
        store.write(daemonRecord)

        val launcher = mock<DaemonLauncher>()
        whenever(launcher.connectToExistingDaemon(daemonRecord)).thenReturn(
            DefaultDaemonClient(fakeDaemon.port, daemonRecord.token),
        )
        val pool = DefaultDaemonPool(store, launcher)
        val context = DaemonContext.fromLivePaths(
            projectPath = project,
            mpsHome = mpsHome,
            javaHome = javaHome
        )
        pool.ensureDaemon(context)

        fakeDaemon.join(5_000)

        assertContains(fakeDaemon.requestsReceived[0], "\"type\":\"ping\"")
        assertContains(fakeDaemon.requestsReceived[0], "\"token\":\"secret\"")
        verify(launcher, never()).startDaemon(any())
    }

    @Test
    fun `removes stale daemon record before attempting autostart`() {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        val mpsHome = tempDir.mpsHome()

        store.write(
            daemonRecord(
                port = 9,
                project = project,
                mpsHome = mpsHome,
                workspace = Path.of("irrelevant"),
            ),
        )

        assertFailsWith<IllegalStateException> {
            DefaultDaemonPool(store, launcherWithUnreachableExistingDaemon()).ensureDaemon(
                DaemonContext.fromLivePaths(
                    projectPath = project,
                    mpsHome = mpsHome,
                    javaHome = Path.of(System.getProperty("java.home"))
                )
            )
        }

        assertEquals(emptyList(), store.readForProject(project))
    }

    @Test
    fun `stops the daemon of the project that uses another MPS home before starting one`() {
        val project = tempDir.mpsProject()
        val mpsHome = tempDir.mpsHome()
        val otherMpsHome = tempDir.mpsHome("other", buildNumber = "MPS-251.1.1")
        val javaHome = Path.of(System.getProperty("java.home"))

        val fakeDaemon = startPrerecordedDaemon(StoppedResponse())
        val store = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home"))
        // No process has pid 999999, so the stopped daemon counts as exited at once.
        val other = daemonRecord(port = fakeDaemon.port, pid = 999_999L, project = project, mpsHome = otherMpsHome, workspace = Path.of("other"))
        store.write(other)

        val context = DaemonContext.fromLivePaths(project, mpsHome, javaHome)
        val started = daemonRecord(port = 9, project = project, mpsHome = mpsHome, workspace = Path.of("started"))
        val launcher = mock<DaemonLauncher>()
        whenever(launcher.startDaemon(context)).thenAnswer {
            store.write(started)
            mock<DaemonClient>()
        }
        val stderr = tapSystemErr { DefaultDaemonPool(store, launcher).ensureDaemon(context) }

        fakeDaemon.join(5_000)
        assertContains(fakeDaemon.requestsReceived.single(), "\"type\":\"stop\"")
        // The fake daemon logs its requests on stderr too.
        assertEquals(
            listOf(
                "mops: stopped the daemon for $project that used MPS MPS-251.1.1 at $otherMpsHome; " +
                    "starting one for MPS MPS-213.7172.1079 at $mpsHome",
            ),
            stderr.lines().filter { it.startsWith("mops:") },
        )
        assertEquals(listOf(started), store.readForProject(project).map { it.record })
    }

    @Test
    fun `finds the daemons of a project in all of its workspaces`() {
        val project = tempDir.mpsProject()
        val otherProject = tempDir.mpsProject("other-project")
        val store = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home"))
        val records = listOf(
            daemonRecord(port = 9, project = project, mpsHome = tempDir.mpsHome(), workspace = Path.of("a")),
            daemonRecord(port = 9, project = project, mpsHome = tempDir.mpsHome("other"), workspace = Path.of("b")),
        )
        records.forEach(store::write)
        store.write(daemonRecord(port = 9, project = otherProject, mpsHome = tempDir.mpsHome(), workspace = Path.of("c")))

        val found = DefaultDaemonPool(store, mock()).findRecords(DaemonPool.Spec.ForProject(project))

        assertEquals(records.toSet(), found.map { it.record }.toSet())
    }

    @Test
    fun `fails when the running daemon has different path macros`() {
        val project = tempDir.mpsProject()
        val mpsHome = tempDir.mpsHome()
        val javaHome = Path.of(System.getProperty("java.home"))
        val macroValue = tempDir.resolve("macro value").createDirectories()

        val fakeDaemon = startPrerecordedDaemon(
            PongResponse(projectPath = project.pathString, mpsHome = mpsHome.pathString, workspacePath = "irrelevant"),
        )
        val store = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home"))
        val record = daemonRecord(port = fakeDaemon.port, project = project, mpsHome = mpsHome, workspace = Path.of("irrelevant"))
            .let { it.copy(context = DaemonContext.fromLivePaths(project, mpsHome, javaHome, mapOf("repo.root" to macroValue))) }
        store.write(record)

        val launcher = mock<DaemonLauncher>()
        whenever(launcher.connectToExistingDaemon(record)).thenReturn(DefaultDaemonClient(fakeDaemon.port, record.token))
        val exception = assertFailsWith<IllegalStateException> {
            DefaultDaemonPool(store, launcher).ensureDaemon(DaemonContext.fromLivePaths(project, mpsHome, javaHome))
        }

        fakeDaemon.join(5_000)
        assertContains(exception.message!!, "different context")
        assertContains(exception.message!!, "repo.root=${macroValue.toRealPath()}")
        assertContains(exception.message!!, "mops daemon stop")
        verify(launcher, never()).startDaemon(any())
    }

    @Test
    fun `removes a stale daemon record of another MPS home before autostart`() {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()

        val staleMps = tempDir.mpsHome(name = "stale-mps")
        val store = DaemonRecordStore.forDaemonHome(daemonHome)

        store.write(
            daemonRecord(
                port = 9,
                project = project,
                mpsHome = staleMps,
                workspace = Path.of("irrelevant"),
            ),
        )

        assertFailsWith<IllegalStateException> {
            DefaultDaemonPool(store, launcherWithUnreachableExistingDaemon()).ensureDaemon(
                DaemonContext.fromLivePaths(
                    projectPath = project,
                    mpsHome = mpsHome,
                    javaHome = Path.of(System.getProperty("java.home"))
                )
            )
        }

        assertEquals(emptyList(), store.readForProject(project))
    }

    @Test
    fun `stop force-kills a daemon that acknowledges the stop but does not exit`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "uses the POSIX sleep command")

        val project = tempDir.mpsProject()
        val mpsHome = tempDir.mpsHome()
        val store = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home"))

        // A real, long-lived process standing in for a daemon JVM that answers the stop but never terminates.
        val stubborn = ProcessBuilder("sleep", "60").start()
        try {
            val fakeDaemon = startPrerecordedDaemon(StoppedResponse())
            val record = daemonRecord(
                port = fakeDaemon.port,
                pid = stubborn.pid(),
                project = project,
                mpsHome = mpsHome,
                workspace = Path.of("/project"),
            )
            store.write(record)

            val outcome = DefaultDaemonPool(
                store,
                mock(),
                stopGrace = Duration.ofMillis(200),
                killGrace = Duration.ofSeconds(5),
            ).stop(store.readForProject(project).single())

            fakeDaemon.join(5_000)
            assertEquals(DaemonPool.StopOutcome.STOPPED, outcome)
            assertFalse(stubborn.isAlive, "the stubborn daemon process should have been force-killed")
            assertEquals(emptyList(), store.readForProject(project), "a stopped daemon's record must be removed")
        } finally {
            stubborn.destroyForcibly()
        }
    }

    @Test
    fun `stop reports a stale record and removes it when the daemon is unreachable`() {
        val project = tempDir.mpsProject()
        val mpsHome = tempDir.mpsHome()
        val store = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home"))

        // Port 9 (discard) refuses the stop, and pid 999999 is not a live process.
        store.write(
            daemonRecord(port = 9, pid = 999_999L, project = project, mpsHome = mpsHome, workspace = Path.of("/x")),
        )

        val outcome = DefaultDaemonPool(store, mock()).stop(store.readForProject(project).single())

        assertEquals(DaemonPool.StopOutcome.ALREADY_GONE, outcome)
        assertEquals(emptyList(), store.readForProject(project))
    }

    private fun launcherWithUnreachableExistingDaemon(): DaemonLauncher {
        val unreachableDaemon = mock<DaemonClient>()
        whenever(unreachableDaemon.ping()).thenThrow(IllegalStateException("daemon is unreachable"))

        val launcher = mock<DaemonLauncher>()
        whenever(launcher.connectToExistingDaemon(any())).thenReturn(unreachableDaemon)
        whenever(launcher.startDaemon(any())).thenReturn(mock<DaemonClient>())
        return launcher
    }
}
