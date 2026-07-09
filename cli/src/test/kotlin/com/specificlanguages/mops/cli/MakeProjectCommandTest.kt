package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class MakeProjectCommandTest {
    @Test
    fun `make project makes the whole project and prints a success summary with exit 0`() {
        val client = mock<DaemonClient>()
        whenever(client.makeProject())
            .thenReturn(MakeResponse(MakeOutcome.SUCCESS, moduleCount = 12, messages = emptyList()))

        val stdout = tapSystemOut { assertEquals(0, run(client)) }

        verify(client).makeProject()
        assertEquals("made 12 module(s): success" + nl, stdout)
    }

    @Test
    fun `make project reports nothing to generate with exit 0`() {
        val client = mock<DaemonClient>()
        whenever(client.makeProject())
            .thenReturn(MakeResponse(MakeOutcome.NOTHING_TO_GENERATE, moduleCount = 0, messages = emptyList()))

        val stdout = tapSystemOut { assertEquals(0, run(client)) }

        assertEquals("nothing to generate" + nl, stdout)
    }

    private fun run(client: DaemonClient, vararg args: String): Int =
        CommandLine(MakeProjectCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .execute(*args)

    private companion object {
        val nl: String = System.lineSeparator()
    }
}
