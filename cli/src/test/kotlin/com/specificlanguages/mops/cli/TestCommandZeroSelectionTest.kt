package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.testing.TestCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.TestRunReport
import com.specificlanguages.mops.protocol.TestRunResponse
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** The zero-tests diagnostic reaches the text output as the daemon worded it, naming the selection. */
@ResourceLock("system-streams")
class TestCommandZeroSelectionTest {
    @Test
    fun `zero discovered tests print the selection diagnostic and exit 1`() {
        val client = mock<DaemonClient>()
        whenever(client.runTests(eq(listOf("my.module", ".tests")), eq(false), any(), any())).thenReturn(TestRunResponse(
            TestRunReport("/runs/1/report.json", outcome = "DISCOVERY_FAILED", phase = "DISCOVERY", complete = true,
                diagnostics = listOf("No tests discovered for selection: model my.module.tests")),
        ))

        var stdout = ""
        val stderr = tapSystemErr {
            stdout = tapSystemOut {
                val command = CommandLine(TestCommand(DaemonClientCommandEnvironment(client))).setExecutionExceptionHandler(PrintErrorAndExit)
                assertEquals(1, command.execute("my.module", ".tests", "--no-build"))
            }
        }

        assertContains(stdout, "DISCOVERY_FAILED")
        assertContains(stdout + stderr, "No tests discovered for selection: model my.module.tests")
    }
}
