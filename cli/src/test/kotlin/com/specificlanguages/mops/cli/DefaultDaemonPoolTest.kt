package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DefaultDaemonClient
import com.specificlanguages.mops.daemoncomms.DefaultDaemonPool
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.daemoncomms.DaemonLauncher
import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.PongResponse
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
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

    private fun launcherWithUnreachableExistingDaemon(): DaemonLauncher {
        val unreachableDaemon = mock<DaemonClient>()
        whenever(unreachableDaemon.ping()).thenThrow(IllegalStateException("daemon is unreachable"))

        val launcher = mock<DaemonLauncher>()
        whenever(launcher.connectToExistingDaemon(any())).thenReturn(unreachableDaemon)
        whenever(launcher.startDaemon(any())).thenReturn(mock<DaemonClient>())
        return launcher
    }
}
