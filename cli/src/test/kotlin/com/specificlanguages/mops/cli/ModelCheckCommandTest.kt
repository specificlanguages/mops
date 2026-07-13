package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@ResourceLock("system-streams")
class ModelCheckCommandTest {
    @Test
    fun `model check requires a model target`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("model", "check")
        }

        assertEquals(2, exitCode)
        assertContains(stderr, "Missing required parameter")
        assertContains(stderr, "MODEL_TARGET")
    }

    @Test
    fun `model check prints a readable severity-sorted list by default`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 20)).thenReturn(
            ModelCheckResponse(
                limit = 20,
                truncated = false,
                totals = ModelCheckFindingCounts(errors = 1, warnings = 1, infos = 0),
                findings = listOf(ERROR_FINDING, WARNING_FINDING),
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(MODEL)
        }

        assertEquals(0, exitCode)
        verify(client).checkModel(MODEL, 20)
        assertContains(stdout, "error\tUnresolved reference\tb\tjetbrains.mps.baseLanguage.structure.VariableReference\t$MODEL/ref")
        assertContains(stdout, "warning\tmodel-level note")
        // The human list is the user-facing payload only: no daemon wrapper fields.
        assertFalse(stdout.contains("\"limit\""), "human output must not leak wrapper fields: $stdout")
    }

    @Test
    fun `model check ends human output with a severity summary line`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 20)).thenReturn(
            ModelCheckResponse(
                limit = 20,
                truncated = false,
                totals = ModelCheckFindingCounts(errors = 1, warnings = 1, infos = 0),
                findings = listOf(ERROR_FINDING, WARNING_FINDING),
            ),
        )

        val stdout = tapSystemOut {
            CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(MODEL)
        }

        assertEquals("1 error, 1 warning, 0 infos", stdout.trim().lines().last())
    }

    @Test
    fun `a model with findings but no errors reads zero errors in the summary`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 20)).thenReturn(
            ModelCheckResponse(
                limit = 20,
                truncated = false,
                totals = ModelCheckFindingCounts(errors = 0, warnings = 3, infos = 12),
                findings = listOf(WARNING_FINDING),
            ),
        )

        val stdout = tapSystemOut {
            CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(MODEL)
        }

        assertEquals("0 errors, 3 warnings, 12 infos", stdout.trim().lines().last())
    }

    @Test
    fun `an empty result summarises as no findings`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 20)).thenReturn(
            ModelCheckResponse(
                limit = 20,
                truncated = false,
                totals = ModelCheckFindingCounts(errors = 0, warnings = 0, infos = 0),
                findings = emptyList(),
            ),
        )

        val stdout = tapSystemOut {
            CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(MODEL)
        }

        assertEquals("no findings", stdout.trim())
    }

    @Test
    fun `model check prints one finding object per line as jsonl`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 20)).thenReturn(
            ModelCheckResponse(
                limit = 20,
                truncated = false,
                totals = ModelCheckFindingCounts(errors = 1, warnings = 1, infos = 0),
                findings = listOf(ERROR_FINDING, WARNING_FINDING),
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--format", "jsonl", MODEL)
        }

        assertEquals(0, exitCode)
        val lines = stdout.trim().lines()
        assertEquals(2, lines.size, "each finding prints on its own line: $stdout")
        assertEquals(ERROR_FINDING, ProtocolJson.decodeFinding(lines[0]))
        assertEquals(WARNING_FINDING, ProtocolJson.decodeFinding(lines[1]))
        // The payload is only the findings, not the daemon response wrapper.
        assertFalse(stdout.contains("\"findings\""), "jsonl must print bare findings, not wrapped responses: $stdout")
        assertFalse(stdout.contains("\"truncated\""), "jsonl must not leak wrapper fields: $stdout")
    }

    @Test
    fun `the summary folds in how many findings were shown when truncated`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 1)).thenReturn(
            ModelCheckResponse(
                limit = 1,
                truncated = true,
                totals = ModelCheckFindingCounts(errors = 3, warnings = 2, infos = 5),
                findings = listOf(ERROR_FINDING),
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "1", MODEL)
        }

        assertEquals(0, exitCode)
        verify(client).checkModel(MODEL, 1)
        // Total counts are over the full set; the shown count folds into the same line.
        assertEquals("3 errors, 2 warnings, 5 infos (showing 1)", stdout.trim().lines().last())
    }

    @Test
    fun `model check rejects an unknown format`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 20)).thenReturn(
            ModelCheckResponse(
                limit = 20,
                truncated = false,
                totals = ModelCheckFindingCounts(errors = 0, warnings = 0, infos = 0),
                findings = emptyList(),
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--format", "xml", MODEL)
        }

        assertEquals(1, exitCode)
        assertContains(stderr, "unknown --format value 'xml'")
    }

    private companion object {
        const val MODEL = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"

        val ERROR_FINDING = ModelCheckFindingJson(
            severity = FindingSeverity.ERROR,
            message = "Unresolved reference",
            node = MpsNodeSummaryJson(
                type = "node",
                name = "b",
                concept = "jetbrains.mps.baseLanguage.structure.VariableReference",
                reference = "$MODEL/ref",
            ),
        )

        val WARNING_FINDING = ModelCheckFindingJson(
            severity = FindingSeverity.WARNING,
            message = "model-level note",
        )
    }
}
