package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.PongResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Runs the CLI in a child JVM with windows-1252 encodings and checks that stdout and stderr still carry UTF-8 bytes.
 * The message comes from a fake daemon over the socket, so the test also covers how the CLI decodes daemon responses.
 */
@Tag("smoke")
class OutputEncodingTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @ParameterizedTest
    @EnumSource
    fun `stdout is UTF-8`(codePage: CodePageProperties) {
        val response = ModelCheckResponse(
            limit = 0,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 1, warnings = 0, infos = 0),
            findings = listOf(ModelCheckFindingJson(severity = FindingSeverity.ERROR, message = MESSAGE)),
        )

        val output = runCli(codePage, response, "check", "model", "--format", "jsonl", MODEL)

        assertEquals(1, output.exitCode, output.stderr.decodeUtf8())
        assertTrue(output.stdout.decodeUtf8().contains(MESSAGE), output.stdout.decodeUtf8())
    }

    @ParameterizedTest
    @EnumSource
    fun `stderr is UTF-8`(codePage: CodePageProperties) {
        val response = DaemonErrorResponse(errorCode = "FAILED", message = MESSAGE, workspacePath = "/project")

        val output = runCli(codePage, response, "check", "model", MODEL)

        assertEquals(1, output.exitCode, output.stderr.decodeUtf8())
        assertTrue(output.stderr.decodeUtf8().contains(MESSAGE), output.stderr.decodeUtf8())
    }

    private class CliOutput(val exitCode: Int, val stdout: ByteArray, val stderr: ByteArray)

    private fun runCli(codePage: CodePageProperties, response: DaemonResponse, vararg args: String): CliOutput {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()
        val javaHome = Path.of(System.getProperty("java.home"))
        val daemon = startPrerecordedDaemon(
            PongResponse(projectPath = project.pathString, mpsHome = mpsHome.pathString, workspacePath = "/project"),
            response,
        )
        DaemonRecordStore.forDaemonHome(daemonHome).write(
            daemonRecord(port = daemon.port, project = project, mpsHome = mpsHome, workspace = Path.of("/project")),
        )

        val stdoutFile = tempDir.resolve("stdout")
        val stderrFile = tempDir.resolve("stderr")
        val process = ProcessBuilder(
            javaHome.resolve("bin/java").pathString,
            *codePage.properties.map { "-D$it=windows-1252" }.toTypedArray(),
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
        daemon.join(5_000)
        return CliOutput(process.exitValue(), stdoutFile.readBytes(), stderrFile.readBytes())
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
    }

    enum class CodePageProperties(vararg val properties: String) {
        /** Western Windows when stdout is a pipe or a file: the streams use the code page, the default charset is UTF-8. */
        STREAMS_ONLY("stdout.encoding", "stderr.encoding", "sun.stdout.encoding", "sun.stderr.encoding"),

        /** The streams and the default charset use the code page, so only explicit charsets produce UTF-8. */
        STREAMS_AND_DEFAULT_CHARSET(*STREAMS_ONLY.properties, "file.encoding"),
    }
}
