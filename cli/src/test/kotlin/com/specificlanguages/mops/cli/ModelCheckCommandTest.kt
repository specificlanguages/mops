package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.check.ModelCheckCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModelCheckSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class ModelCheckCommandTest {
    @Test
    fun `model check requires a model target`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("check", "model")
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

        assertEquals(1, exitCode, "an error finding fails the check")
        verify(client).checkModel(MODEL, 20)
        assertContains(stdout, "error\tUnresolved reference\tb\tjetbrains.mps.baseLanguage.structure.VariableReference\t$MODEL/ref")
        assertContains(stdout, "warning\tmodel-level note")
        // The human list is the user-facing payload only: no daemon wrapper fields.
        assertFalse(stdout.contains("\"limit\""), "human output must not leak wrapper fields: $stdout")
    }

    @Test
    fun `model check ends human output with a severity summary metadata line`() {
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

        assertEquals("# 1 error, 1 warning, 0 infos", stdout.trim().lines().last())
    }

    @Test
    fun `a model with findings but no errors reads zero errors in the summary and passes`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 20)).thenReturn(
            ModelCheckResponse(
                limit = 20,
                truncated = false,
                totals = ModelCheckFindingCounts(errors = 0, warnings = 3, infos = 12),
                findings = listOf(WARNING_FINDING),
            ),
        )

        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(MODEL)
        }

        assertEquals(0, exitCode, "warnings and infos alone do not fail the check")
        assertEquals("# 0 errors, 3 warnings, 12 infos", stdout.trim().lines().last())
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

        assertEquals("# no findings", stdout.trim())
    }

    @Test
    fun `jsonl output is one finding per line followed by a summary record`() {
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
                .execute("--format", "JsOnL", MODEL)
        }

        assertEquals(1, exitCode)
        val lines = stdout.trim().lines()
        assertEquals(3, lines.size, "each finding prints on its own line, then the summary: $stdout")
        assertEquals(ERROR_FINDING, ProtocolJson.decodeFinding(lines[0]))
        assertEquals(WARNING_FINDING, ProtocolJson.decodeFinding(lines[1]))
        assertEquals(
            ModelCheckSummaryJson(
                shown = 2,
                total = 2,
                errors = 1,
                warnings = 1,
                infos = 0,
                limit = 20,
                truncated = false,
            ),
            ProtocolJson.decodeCheckSummary(lines[2]),
        )
        // The payload is the findings and the summary, not the daemon response wrapper.
        assertFalse(stdout.contains("\"findings\""), "jsonl must print bare findings, not wrapped responses: $stdout")
    }

    @Test
    fun `a truncated jsonl summary names the option that lifts the limit`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModel(MODEL, 1)).thenReturn(
            ModelCheckResponse(
                limit = 1,
                truncated = true,
                totals = ModelCheckFindingCounts(errors = 0, warnings = 2, infos = 5),
                findings = listOf(WARNING_FINDING),
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--format", "jsonl", "--limit", "1", MODEL)
        }

        assertEquals(0, exitCode)
        val lines = stdout.trim().lines()
        assertEquals(2, lines.size, stdout)
        assertEquals(
            ModelCheckSummaryJson(
                shown = 1,
                total = 7,
                errors = 0,
                warnings = 2,
                infos = 5,
                limit = 1,
                truncated = true,
                hint = "pass --limit 0 for all",
            ),
            ProtocolJson.decodeCheckSummary(lines[1]),
        )
    }

    @Test
    fun `a truncated human summary is followed by a truncation metadata line`() {
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

        // Totals and the exit decision are over the full set, not the shown slice.
        assertEquals(1, exitCode)
        verify(client).checkModel(MODEL, 1)
        assertEquals(
            listOf(
                "error\tUnresolved reference\tb\tjetbrains.mps.baseLanguage.structure.VariableReference\t$MODEL/ref",
                "# 3 errors, 2 warnings, 5 infos",
                "# truncated: showing 1 of 10 findings; pass --limit 0 for all",
            ),
            stdout.trim().lines(),
        )
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

        assertEquals(2, exitCode)
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
