package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.check.ProjectCheckCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class ProjectCheckCommandTest {
    @Test
    fun `project check checks all project modules with the default limit`() {
        val client = mock<DaemonClient>()
        whenever(client.checkProject(20)).thenReturn(NO_FINDINGS)

        val stdout = tapSystemOut {
            val exitCode = CommandLine(ProjectCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute()
            assertEquals(0, exitCode)
        }

        verify(client).checkProject(20)
        assertEquals("# no findings", stdout.trim())
    }

    @Test
    fun `project check exits 1 when the totals count an error, in both formats`() {
        val client = mock<DaemonClient>()
        whenever(client.checkProject(20)).thenReturn(ONE_ERROR)

        for (args in listOf(emptyArray(), arrayOf("--format", "jsonl"))) {
            var exitCode = Int.MIN_VALUE
            val stdout = tapSystemOut {
                exitCode = CommandLine(ProjectCheckCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute(*args)
            }
            assertEquals(1, exitCode, "${args.toList()}: $stdout")
        }
    }

    @Test
    fun `project check exits 0 when the findings are warnings only`() {
        val client = mock<DaemonClient>()
        whenever(client.checkProject(20)).thenReturn(ONE_WARNING)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ProjectCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute()
        }

        assertEquals(0, exitCode, stdout)
        assertEquals(listOf("warning\tmodel-level note", "# 0 errors, 1 warning, 0 infos"), stdout.trim().lines())
    }

    private companion object {
        val NO_FINDINGS = ModelCheckResponse(
            limit = 20,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 0, warnings = 0, infos = 0),
            findings = emptyList(),
        )

        val ONE_ERROR = ModelCheckResponse(
            limit = 20,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 1, warnings = 0, infos = 0),
            findings = listOf(ModelCheckFindingJson(severity = FindingSeverity.ERROR, message = "Unresolved reference")),
        )

        val ONE_WARNING = ModelCheckResponse(
            limit = 20,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 0, warnings = 1, infos = 0),
            findings = listOf(ModelCheckFindingJson(severity = FindingSeverity.WARNING, message = "model-level note")),
        )
    }
}
