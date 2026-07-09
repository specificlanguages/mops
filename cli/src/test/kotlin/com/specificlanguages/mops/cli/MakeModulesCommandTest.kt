package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.MakeMessageJson
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class MakeModulesCommandTest {
    @Test
    fun `make modules forwards the module names and prints a success summary with exit 0`() {
        val client = mock<DaemonClient>()
        whenever(client.makeModules(listOf("moduleA", "moduleB")))
            .thenReturn(MakeResponse(MakeOutcome.SUCCESS, moduleCount = 3, messages = emptyList()))

        val stdout = tapSystemOut {
            assertEquals(0, run(client, "moduleA", "moduleB"))
        }

        verify(client).makeModules(listOf("moduleA", "moduleB"))
        assertEquals("made 3 module(s): success" + nl, stdout)
    }

    @Test
    fun `make modules prints errors to stderr and exits 1 on failure`() {
        val client = mock<DaemonClient>()
        whenever(client.makeModules(listOf("moduleA"))).thenReturn(
            MakeResponse(
                MakeOutcome.FAILED,
                moduleCount = 2,
                messages = listOf(
                    MakeMessageJson(MakeMessageKind.ERROR, "cannot resolve reference"),
                    MakeMessageJson(MakeMessageKind.WARNING, "deprecated usage"),
                ),
            ),
        )

        val stderr = tapSystemErr {
            val stdout = tapSystemOut { assertEquals(1, run(client, "moduleA")) }
            assertEquals("made 2 module(s): FAILED (1 error(s))" + nl, stdout)
        }

        assertEquals("error\tcannot resolve reference" + nl + "warning\tdeprecated usage" + nl, stderr)
    }

    @Test
    fun `make modules can emit json`() {
        val client = mock<DaemonClient>()
        val response = MakeResponse(MakeOutcome.SUCCESS, moduleCount = 1, messages = emptyList())
        whenever(client.makeModules(listOf("moduleA"))).thenReturn(response)

        val stdout = tapSystemOut { assertEquals(0, run(client, "--json", "moduleA")) }

        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    private fun run(client: DaemonClient, vararg args: String): Int =
        CommandLine(MakeModulesCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .execute(*args)

    private companion object {
        val nl: String = System.lineSeparator()
    }
}
