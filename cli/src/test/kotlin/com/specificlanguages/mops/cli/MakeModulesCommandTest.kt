package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.make.MakeModulesCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.MakeGenerationOptions
import com.specificlanguages.mops.protocol.MakeMessageJson
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeMessageLevel
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.PerformanceTracingLevel
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class MakeModulesCommandTest {
    @Test
    fun `make modules forwards the module names and prints a success summary with exit 0`() {
        val client = mock<DaemonClient>()
        whenever(client.makeModules(listOf("moduleA", "moduleB"), MakeGenerationOptions()))
            .thenReturn(MakeResponse(MakeOutcome.SUCCESS, moduleCount = 3, messages = emptyList()))

        val stdout = tapSystemOut {
            assertEquals(0, run(client, "moduleA", "moduleB"))
        }

        verify(client).makeModules(listOf("moduleA", "moduleB"), MakeGenerationOptions())
        assertEquals("made 3 module(s): success" + nl, stdout)
    }

    @Test
    fun `make modules prints errors to stderr and exits 1 on failure`() {
        val client = mock<DaemonClient>()
        whenever(client.makeModules(listOf("moduleA"), MakeGenerationOptions())).thenReturn(
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
        whenever(client.makeModules(listOf("moduleA"), MakeGenerationOptions())).thenReturn(response)

        val stdout = tapSystemOut { assertEquals(0, run(client, "--json", "moduleA")) }

        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    @Test
    fun `make modules forwards the generation options`() {
        val client = mock<DaemonClient>()
        val options = MakeGenerationOptions(
            threads = 4,
            strict = true,
            messages = MakeMessageLevel.INFO,
            saveTransientModels = true,
            performanceTracing = PerformanceTracingLevel.LANGUAGES,
        )
        whenever(client.makeModules(listOf("moduleA"), options))
            .thenReturn(MakeResponse(MakeOutcome.SUCCESS, moduleCount = 1, messages = emptyList()))

        tapSystemOut {
            val exitCode = run(
                client, "--threads", "4", "--strict", "--messages", "info", "--save-transient-models",
                "--performance-tracing", "languages", "moduleA",
            )
            assertEquals(0, exitCode)
        }

        verify(client).makeModules(listOf("moduleA"), options)
    }

    @Test
    fun `make modules forwards the negated generation options`() {
        val client = mock<DaemonClient>()
        val options = MakeGenerationOptions(strict = false, saveTransientModels = false)
        whenever(client.makeModules(listOf("moduleA"), options))
            .thenReturn(MakeResponse(MakeOutcome.SUCCESS, moduleCount = 1, messages = emptyList()))

        tapSystemOut { assertEquals(0, run(client, "--no-strict", "--no-save-transient-models", "moduleA")) }

        verify(client).makeModules(listOf("moduleA"), options)
    }

    @Test
    fun `make modules prints info messages with their kind`() {
        val client = mock<DaemonClient>()
        whenever(client.makeModules(listOf("moduleA"), MakeGenerationOptions(messages = MakeMessageLevel.INFO)))
            .thenReturn(
                MakeResponse(
                    MakeOutcome.SUCCESS,
                    moduleCount = 1,
                    messages = listOf(MakeMessageJson(MakeMessageKind.INFO, "generation completed")),
                ),
            )

        val stderr = tapSystemErr { tapSystemOut { assertEquals(0, run(client, "--messages", "info", "moduleA")) } }

        assertEquals("info\tgeneration completed" + nl, stderr)
    }

    @Test
    fun `make modules rejects an unknown message level as a usage error`() {
        val client = mock<DaemonClient>()

        val stderr = tapSystemErr { assertEquals(2, run(client, "--messages", "verbose", "moduleA")) }

        assertContains(stderr, "verbose (expected one of error, warning, info)")
        verifyNoInteractions(client)
    }

    @Test
    fun `make modules rejects a thread count below 1 as a usage error`() {
        val client = mock<DaemonClient>()

        val stderr = tapSystemErr { assertEquals(2, run(client, "--threads", "0", "moduleA")) }

        assertContains(stderr, "Invalid value for option '--threads': 0 (must be at least 1)")
        verifyNoInteractions(client)
    }

    private fun run(client: DaemonClient, vararg args: String): Int =
        CommandLine(MakeModulesCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .execute(*args)

    private companion object {
        val nl: String = System.lineSeparator()
    }
}
