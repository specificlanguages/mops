package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.MakeMessageJson
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.PongResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

/**
 * Runs the CLI in a child JVM whose stream and default encodings are all windows-1252 and checks that the CLI still
 * writes UTF-8 to stdout, stderr and the daemon socket.
 */
@Tag("smoke")
class OutputEncodingTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `stdout is UTF-8`() {
        val response = checkResponse(
            findings = listOf(ModelCheckFindingJson(severity = FindingSeverity.ERROR, message = MESSAGE)),
            limit = 0,
        )

        val output = runCli(response, "check", "model", "--format", "jsonl", MODEL)

        assertEquals(1, output.exitCode, output.stderr.decodeUtf8())
        assertTrue(output.stdout.decodeUtf8().contains(MESSAGE), output.stdout.decodeUtf8())
    }

    @Test
    fun `stderr written through System err is UTF-8`() {
        val response = MakeResponse(
            outcome = MakeOutcome.FAILED,
            moduleCount = 1,
            messages = listOf(MakeMessageJson(kind = MakeMessageKind.ERROR, text = MESSAGE)),
        )

        val output = runCli(response, "make", "project")

        assertEquals(1, output.exitCode, output.stderr.decodeUtf8())
        assertTrue(output.stderr.decodeUtf8().contains(MESSAGE), output.stderr.decodeUtf8())
    }

    @Test
    fun `stderr written by picocli is UTF-8`() {
        val output = runCli(null, "--$WORD")

        assertEquals(2, output.exitCode, output.stderr.decodeUtf8())
        assertTrue(output.stderr.decodeUtf8().contains("Unknown option: '--$WORD'"), output.stderr.decodeUtf8())
    }

    @Test
    fun `daemon requests are UTF-8`() {
        val output = runCli(DaemonErrorResponse(errorCode = "FAILED", message = "failed", workspacePath = "/project"),
            "find", "root-by-name", WORD)

        assertEquals(1, output.exitCode, output.stderr.decodeUtf8())
        assertTrue(output.daemonRequests.last().contains(WORD), output.daemonRequests.last())
    }

    private class CliOutput(val exitCode: Int, val stdout: ByteArray, val stderr: ByteArray, val daemonRequests: List<String>)

    /** Runs the CLI against a fake daemon that answers a ping and then [response], or against no daemon if it is null. */
    private fun runCli(response: DaemonResponse?, vararg args: String): CliOutput {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()
        val javaHome = Path.of(System.getProperty("java.home"))
        val daemon = response?.let {
            val pong = PongResponse(projectPath = project.pathString, mpsHome = mpsHome.pathString, workspacePath = "/project")
            startPrerecordedDaemon(pong, it)
        }
        if (daemon != null) {
            DaemonRecordStore.forDaemonHome(daemonHome).write(
                daemonRecord(port = daemon.port, project = project, mpsHome = mpsHome, workspace = Path.of("/project")),
            )
        }

        val stdoutFile = tempDir.resolve("stdout")
        val stderrFile = tempDir.resolve("stderr")
        val process = ProcessBuilder(
            javaHome.resolve("bin/java").pathString,
            *WINDOWS_1252_PROPERTIES.map { "-D$it=windows-1252" }.toTypedArray(),
            "-cp", System.getProperty("java.class.path"),
            "com.specificlanguages.mops.cli.MainKt",
            "--daemon-home", daemonHome.pathString,
            "--mps-home", mpsHome.pathString,
            "--java-home", javaHome.pathString,
            *args,
        )
            .directory(project.toFile())
            .redirectOutput(stdoutFile.toFile())
            .redirectError(stderrFile.toFile())
            .start()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "CLI did not exit")
        daemon?.join(5_000)
        return CliOutput(
            process.exitValue(),
            stdoutFile.readBytes(),
            stderrFile.readBytes(),
            daemon?.requestsReceived.orEmpty(),
        )
    }

    /** Decodes strictly, so bytes in any other encoding fail the test instead of turning into replacement characters. */
    private fun ByteArray.decodeUtf8(): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()

    private companion object {
        const val MODEL = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"
        const val MESSAGE = "‹+› cannot be applied to types ‹boolean› and ‹number[1]{0}›"
        const val WORD = "‹boolean›"

        /** Every JVM property that selects the encoding of stdout, stderr or the default charset. */
        val WINDOWS_1252_PROPERTIES = listOf(
            "stdout.encoding",
            "stderr.encoding",
            "sun.stdout.encoding",
            "sun.stderr.encoding",
            "file.encoding",
        )
    }
}
