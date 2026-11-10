package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonPaths
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

/**
 * The test worker keeps its system directory in the daemon workspace between runs and starts with a fresh one after a
 * run that did not end cleanly. The CLI runs in its own process without the system directory shared by the tests of
 * this test JVM.
 */
@ResourceLock("system-streams")
class TestWorkerSystemIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `the workspace keeps the worker system directory between runs and deletes it after a timed-out run`() {
        val project = copyTestingProject(tempDir.resolve("project"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        val cli = InstalledCli(project, home, tempDir, sharedWorkerSystem = false)
        try {
            val first = cli.run("test", "mops.tests", ".ordinary", "OrdinaryPass", "--json")
            assertEquals(0, first.exitCode, first.output)
            val workerSystem = DaemonPaths(home).workspacesOf(project).single().testWorkerSystemDir()
            assertTrue(workerSystem.isDirectory(), first.output)
            val marker = workerSystem.resolve("kept-between-runs").createFile()

            val second = cli.run("test", "mops.tests", ".ordinary", "OrdinaryPass", "--no-build", "--json")
            assertEquals(0, second.exitCode, second.output)
            assertTrue(marker.exists(), "the second run must reuse the worker system directory of the first")

            // The limit counts from the start of test execution: aCompleted passes within it, zSlow sleeps 600 seconds.
            val timed = cli.run("test", "mops.tests", ".slow", "SlowTest", "--no-build", "--json", "--timeout", "15")
            assertEquals(1, timed.exitCode, timed.output)
            assertEquals("TIMED_OUT", ProtocolJson.testReport.decode(timed.stdout).outcome, timed.output)
            assertFalse(workerSystem.exists(), "a timed-out run must delete the worker system directory")
        } finally { stopDaemons(project, home) }
    }
}
