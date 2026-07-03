package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@ResourceLock("system-streams")
class DaemonStartupIntegrationTest {

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `daemon startup fails and ping surfaces the error for a project without modules xml`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        Files.delete(project.resolve(".mps").resolve("modules.xml"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        val ping = runCommandLine(
            project,
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "ping",
        )

        try {
            assertNotEquals(0, ping.exitCode, ping.output)

            val logPath = Regex("Daemon log: (.+)").find(ping.stderr)?.groupValues?.get(1)?.trim()
            assertNotNull(logPath, "startup failure should reference the daemon log:\n${ping.output}")

            val log = Path.of(logPath).readText()
            assertContains(log, "EMPTY_PROJECT")
            assertContains(log, ".mps/modules.xml")
            assertContains(log, "mps-json")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }
}
