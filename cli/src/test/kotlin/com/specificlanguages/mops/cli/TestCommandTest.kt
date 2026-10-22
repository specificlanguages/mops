package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.testing.TestCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.TestResult
import com.specificlanguages.mops.protocol.TestRunReport
import com.specificlanguages.mops.protocol.TestRunResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class TestCommandTest {
    @Test
    fun `test prints the summary and report path on stdout and the diagnostics on stderr`() {
        val client = mock<DaemonClient>()
        whenever(client.runTests(eq(listOf("my.module")), eq(false), any(), anyOrNull()))
            .thenReturn(TestRunResponse(failedReport()))

        var stdout = ""
        val stderr = tapSystemErr {
            stdout = tapSystemOut { assertEquals(1, run(client, "--no-build", "my.module")) }
        }

        assertEquals("TEST_FAILED: 1 passed, 1 failed" + nl + "Report: /workspace/test-runs/run-1/report.json" + nl, stdout)
        assertEquals("Ant test worker exited 1" + nl + "a second diagnostic" + nl, stderr)
    }

    @Test
    fun `test prints the report as json when requested`() {
        val client = mock<DaemonClient>()
        val report = failedReport()
        whenever(client.runTests(eq(emptyList()), eq(true), any(), anyOrNull())).thenReturn(TestRunResponse(report))

        var stdout = ""
        val stderr = tapSystemErr { stdout = tapSystemOut { assertEquals(1, run(client, "--json")) } }

        assertEquals(report, ProtocolJson.decodeTestReport(stdout))
        assertEquals("", stderr)
    }

    private fun run(client: DaemonClient, vararg args: String): Int =
        CommandLine(TestCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .execute(*args)

    private fun failedReport() = TestRunReport(
        reportPath = "/workspace/test-runs/run-1/report.json",
        outcome = "TEST_FAILED",
        phase = "DONE",
        complete = true,
        discovered = 2,
        results = listOf(
            TestResult(id = "c", name = "MyCase", kind = "CONTAINER", status = "FAILED"),
            TestResult(id = "t1", name = "passes", kind = "TEST", status = "PASSED"),
            TestResult(id = "t2", name = "fails", kind = "TEST", status = "FAILED", detail = "expected 1 but was 2"),
        ),
        diagnostics = listOf("Ant test worker exited 1" + nl + "second line of the first diagnostic", "a second diagnostic"),
    )

    private companion object {
        val nl: String = System.lineSeparator()
    }
}
