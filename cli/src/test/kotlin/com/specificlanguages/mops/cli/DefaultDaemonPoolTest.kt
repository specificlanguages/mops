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
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

        assertNull(store.read(project))
    }

    @Test
    fun `fails when project is owned by different context`() {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()
        val otherMpsHome = tempDir.mpsHome("other")

        val fakeDaemon = startPrerecordedDaemon(
            PongResponse(
                projectPath = project.pathString,
                mpsHome = otherMpsHome.pathString,
                workspacePath = "irrelevant"
            ),
        )
        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        val record = daemonRecord(
            port = fakeDaemon.port,
            project = project,
            mpsHome = otherMpsHome,
            workspace = Path.of("irrelevant"),
        )
        store.write(record)

        val launcher = mock<DaemonLauncher>()
        whenever(launcher.connectToExistingDaemon(record)).thenReturn(
            DefaultDaemonClient(fakeDaemon.port, record.token),
        )
        val exception = assertFailsWith<IllegalStateException> {
            DefaultDaemonPool(store, launcher).ensureDaemon(
                DaemonContext.fromLivePaths(
                    projectPath = project,
                    mpsHome = mpsHome,
                    javaHome = Path.of(System.getProperty("java.home"))
                )
            )
        }

        fakeDaemon.join(5_000)
        assertContains(exception.message!!, "different context")
        assertContains(exception.message!!, otherMpsHome.pathString)
        verify(launcher, never()).startDaemon(any())
    }

    @Test
    fun `removes stale daemon record before rejecting a different mps home`() {
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

        assertNull(store.read(project))
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
            ).stop(store.read(project)!!)

            fakeDaemon.join(5_000)
            assertEquals(DaemonPool.StopOutcome.STOPPED, outcome)
            assertFalse(stubborn.isAlive, "the stubborn daemon process should have been force-killed")
            assertNull(store.read(project), "a stopped daemon's record must be removed")
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

        val outcome = DefaultDaemonPool(store, mock()).stop(store.read(project)!!)

        assertEquals(DaemonPool.StopOutcome.ALREADY_GONE, outcome)
        assertNull(store.read(project))
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
