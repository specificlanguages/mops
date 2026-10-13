package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModelCheckSummaryJson
import com.specificlanguages.mops.protocol.PongResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock

/**
 * Runs `check` through the whole CLI against a prerecorded daemon to check the contract a pipeline relies on: with
 * `--format jsonl` every stdout line is a JSON object and the last one is the summary record, in text mode every
 * non-`#` line is a finding, and the exit code reports error findings in both formats.
 */
@ResourceLock("system-streams")
class CheckOutputPipeTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `jsonl output is one JSON object per line ending in the summary record`() {
        val output = runCheck(truncatedResponse(), "check", "model", "--format", "jsonl", "--limit", "2", MODEL)

        assertEquals(1, output.exitCode, output.stderr)
        val records = output.stdout.lines().map { Json.parseToJsonElement(it).jsonObject }
        assertEquals(3, records.size, output.stdout)
        assertEquals(listOf(null, null, "summary"), records.map { it["type"]?.jsonPrimitive?.content })
        assertEquals(
            ModelCheckSummaryJson(
                shown = 2,
                total = 4,
                errors = 1,
                warnings = 2,
                infos = 1,
                limit = 2,
                truncated = true,
                hint = "pass --limit 0 for all",
            ),
            ProtocolJson.decodeCheckSummary(output.stdout.lines().last()),
        )
    }

    @Test
    fun `text output has exactly one non-metadata line per finding when truncated`() {
        val response = truncatedResponse()
        val output = runCheck(response, "check", "model", "--limit", "2", MODEL)

        assertEquals(1, output.exitCode, output.stderr)
        assertEquals(response.findings.size, output.stdout.lines().filterNot { it.startsWith("#") }.size, output.stdout)
        assertEquals(
            listOf("# 1 error, 2 warnings, 1 info", "# truncated: showing 2 of 4 findings; pass --limit 0 for all"),
            output.stdout.lines().filter { it.startsWith("#") },
        )
    }

    private class CliOutput(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runCheck(response: ModelCheckResponse, vararg args: String): CliOutput {
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

        var exitCode = Int.MIN_VALUE
        var stdout = ""
        val stderr = tapSystemErr {
            stdout = tapSystemOut {
                exitCode = newCommandLine(workingDirectory = project).execute(
                    "--daemon-home", daemonHome.pathString,
                    "--mps-home", mpsHome.pathString,
                    "--java-home", javaHome.pathString,
                    *args,
                )
            }
        }
        daemon.join(5_000)
        return CliOutput(exitCode, stdout.trimEnd('\r', '\n'), stderr)
    }

    private fun truncatedResponse() = ModelCheckResponse(
        limit = 2,
        truncated = true,
        totals = ModelCheckFindingCounts(errors = 1, warnings = 2, infos = 1),
        findings = listOf(
            ModelCheckFindingJson(severity = FindingSeverity.ERROR, message = "Unresolved reference"),
            ModelCheckFindingJson(severity = FindingSeverity.WARNING, message = "model-level note"),
        ),
    )

    private companion object {
        const val MODEL = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"
    }
}
