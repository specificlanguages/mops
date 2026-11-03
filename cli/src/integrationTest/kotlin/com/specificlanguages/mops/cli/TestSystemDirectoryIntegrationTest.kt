package com.specificlanguages.mops.cli

import com.specificlanguages.mops.launcher.MpsBuildProperties
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.*
import kotlin.test.*

/** Test runs share persistent IntelliJ system directories, and a run that finds one in use falls back visibly. */
@ResourceLock("system-streams")
class TestSystemDirectoryIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `a run whose worker system directory is in use uses a fresh one and says so`() {
        val project = copyTestProject("base-language-sandbox", tempDir.resolve("project"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        val workerSystems = Path.of(System.getenv("MOPS_TEST_SYSTEM_CACHE"))
            .resolve(requireNotNull(MpsBuildProperties.fromMpsHome(Path.of(System.getProperty("test.mpsHome")))?.buildNumber))
            .createDirectories()
        try {
            val result = FileChannel.open(workerSystems.resolve("worker.lock"), CREATE, WRITE).use { channel ->
                channel.lock().use {
                    runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), "test", "--no-build", "--json")
                }
            }
            assertEquals(1, result.exitCode, result.output)
            val report = ProtocolJson.testReport.decode(result.stdout)
            assertEquals("DISCOVERY_FAILED", report.outcome, result.output)
            val runDirectory = Path.of(report.reportPath).parent
            assertContains(report.diagnostics, "The test worker's system directory ${workerSystems.resolve("worker")} is in use " +
                "by another test run; this run uses the fresh directory ${runDirectory.resolve("test-system")} and indexes the " +
                "MPS distribution again.")
            assertTrue(runDirectory.resolve("test-system").isDirectory(), result.output)
        } finally { stopDaemons(project, home) }
    }
}
