package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.check.ModuleCheckCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModelCheckSummaryJson
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class ModuleCheckCommandTest {
    @Test
    fun `module check requires at least one Project Module`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("check", "module")
        }

        assertEquals(2, exitCode)
        assertContains(stderr, "Missing required parameter")
        assertContains(stderr, "MODULE")
    }

    @Test
    fun `module check checks every named project module with the default limit`() {
        val client = mock<DaemonClient>()
        val modules = listOf("FOO", "BAR", "BAZ")
        whenever(client.checkModules(modules, 20)).thenReturn(NO_FINDINGS)

        val stdout = tapSystemOut {
            val exitCode = CommandLine(ModuleCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(*modules.toTypedArray())
            assertEquals(0, exitCode)
        }

        verify(client).checkModules(modules, 20)
        assertEquals("# checked 3 roots: no findings", stdout.trim())
    }

    @Test
    fun `module check exits 1 when the totals count an error, in both formats`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModules(listOf("FOO"), 20)).thenReturn(ONE_ERROR)

        for (args in listOf(arrayOf("FOO"), arrayOf("--format", "jsonl", "FOO"))) {
            var exitCode = Int.MIN_VALUE
            val stdout = tapSystemOut {
                exitCode = CommandLine(ModuleCheckCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute(*args)
            }
            assertEquals(1, exitCode, "${args.toList()}: $stdout")
        }
    }

    @Test
    fun `module check exits 0 when the findings are warnings only`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModules(listOf("FOO"), 20)).thenReturn(ONE_WARNING)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModuleCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("FOO")
        }

        assertEquals(0, exitCode, stdout)
        assertEquals(listOf("warning\tmodel-level note", "# checked 3 roots: 0 errors, 1 warning, 0 infos"), stdout.trim().lines())
    }

    @Test
    fun `module check ends truncated jsonl output with a summary record naming the lifting option`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModules(listOf("FOO"), 1)).thenReturn(
            ModelCheckResponse(
                rootsChecked = 3,
                limit = 1,
                truncated = true,
                totals = ModelCheckFindingCounts(errors = 0, warnings = 3, infos = 0),
                findings = listOf(ModelCheckFindingJson(severity = FindingSeverity.WARNING, message = "model-level note")),
            ),
        )

        val stdout = tapSystemOut {
            CommandLine(ModuleCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--format", "jsonl", "--limit", "1", "FOO")
        }

        val lines = stdout.trim().lines()
        assertEquals(2, lines.size, stdout)
        assertEquals(
            ModelCheckSummaryJson(
                rootsChecked = 3,
                shown = 1,
                total = 3,
                errors = 0,
                warnings = 3,
                infos = 0,
                limit = 1,
                truncated = true,
                hint = "pass --limit 0 for all",
            ),
            ProtocolJson.checkSummary.decode(lines[1]),
        )
    }

    @Test
    fun `module check fails when the named modules have no roots`() {
        val client = mock<DaemonClient>()
        val modules = listOf("FOO", "BAR")
        whenever(client.checkModules(modules, 20)).thenReturn(EMPTY)
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = CommandLine(ModuleCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(*modules.toTypedArray())
        }

        assertEquals(1, exitCode)
        assertEquals("modules FOO, BAR have no roots; nothing was checked", stderr.trim())
    }

    @Test
    fun `module check of one module without roots names it in the singular`() {
        val client = mock<DaemonClient>()
        whenever(client.checkModules(listOf("FOO"), 20)).thenReturn(EMPTY)

        val stderr = tapSystemErr {
            CommandLine(ModuleCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("FOO")
        }

        assertEquals("module FOO has no roots; nothing was checked", stderr.trim())
    }

    private companion object {
        val EMPTY = ModelCheckResponse(
            rootsChecked = 0,
            limit = 20,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 0, warnings = 0, infos = 0),
            findings = emptyList(),
        )

        val NO_FINDINGS = ModelCheckResponse(
            rootsChecked = 3,
            limit = 20,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 0, warnings = 0, infos = 0),
            findings = emptyList(),
        )

        val ONE_ERROR = ModelCheckResponse(
            rootsChecked = 3,
            limit = 20,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 1, warnings = 0, infos = 0),
            findings = listOf(ModelCheckFindingJson(severity = FindingSeverity.ERROR, message = "Unresolved reference")),
        )

        val ONE_WARNING = ModelCheckResponse(
            rootsChecked = 3,
            limit = 20,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 0, warnings = 1, infos = 0),
            findings = listOf(ModelCheckFindingJson(severity = FindingSeverity.WARNING, message = "model-level note")),
        )
    }
}
