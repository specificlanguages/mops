package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonPaths
import com.specificlanguages.mops.protocol.DaemonWorkspace
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.AssertionFailedError
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

@Tag("smoke")
class AwaitCliTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `a CLI that does not finish in time fails with the daemon log`() {
        val home = tempDir.resolve("daemon-home")
        DaemonWorkspace(DaemonPaths(home).projects.resolve("0123456789ab")).logFile()
            .apply { parent.createDirectories() }
            .writeText("2026-11-03T22:04:00Z initializing MPS for project sample\n")
        val program = tempDir.resolve("Hang.java").apply {
            writeText("class Hang { public static void main(String[] args) throws Exception { Thread.sleep(60_000); } }")
        }
        val stdout = tempDir.resolve("stdout.txt")
        val stderr = tempDir.resolve("stderr.txt")
        val process = ProcessBuilder(javaExecutable().pathString, program.pathString)
            .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()

        val failure = assertFailsWith<AssertionFailedError> {
            awaitCli(process, stdout, stderr, home, timeout = Duration.ofSeconds(1))
        }

        assertContains(failure.message.orEmpty(), "CLI timed out")
        assertContains(failure.message.orEmpty(), "initializing MPS for project sample")
    }
}
