package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModuleLoadDiagnosticJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ModuleLoadSummary
import com.specificlanguages.mops.protocol.ModulesDiagnosticsResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class DiagnoseModulesCommandTest {
    @Test
    fun `diagnose modules prints the summary, failed rows with flattened root causes, and a note`() {
        val client = mock<DaemonClient>()
        whenever(client.diagnoseModules()).thenReturn(sampleResponse())

        val stdout = tapSystemOut { assertEquals(0, run(client)) }

        assertEquals(
            "modules\t1/2 loaded\t1 failed" + nl +
                "org.example.expr\tlanguage\tBROKEN_DEPENDENCIES" + nl +
                "\tjetbrains.mps.lang.editor.tooltips\tABSENT" + nl +
                "note\tmodules without a Java facet are not listed; diagnose one with: mops diagnose module <module>" + nl,
            stdout,
        )
    }

    @Test
    fun `diagnose modules also lists loaded modules with --all`() {
        val client = mock<DaemonClient>()
        whenever(client.diagnoseModules()).thenReturn(sampleResponse())

        val stdout = tapSystemOut { assertEquals(0, run(client, "--all")) }

        assertEquals(
            "modules\t1/2 loaded\t1 failed" + nl +
                "org.example.base\tlanguage\tloaded" + nl +
                "org.example.expr\tlanguage\tBROKEN_DEPENDENCIES" + nl +
                "\tjetbrains.mps.lang.editor.tooltips\tABSENT" + nl +
                "note\tmodules without a Java facet are not listed; diagnose one with: mops diagnose module <module>" + nl,
            stdout,
        )
    }

    @Test
    fun `diagnose modules prints a leaf reason without root cause lines`() {
        val client = mock<DaemonClient>()
        whenever(client.diagnoseModules()).thenReturn(
            ModulesDiagnosticsResponse(
                summary = ModuleLoadSummary(total = 1, loaded = 0, failed = 1),
                modules = listOf(
                    ModuleLoadDiagnosticJson(
                        module = "org.example.base",
                        kind = "language",
                        present = true,
                        loaded = false,
                        problem = ModuleLoadProblemJson(module = "org.example.base", reason = "NOT_BUILT", detail = "not built"),
                    ),
                ),
            ),
        )

        val stdout = tapSystemOut { assertEquals(0, run(client)) }

        assertEquals(
            "modules\t0/1 loaded\t1 failed" + nl +
                "org.example.base\tlanguage\tNOT_BUILT" + nl +
                "note\tmodules without a Java facet are not listed; diagnose one with: mops diagnose module <module>" + nl,
            stdout,
        )
    }

    @Test
    fun `diagnose modules prints the response object as json when requested`() {
        val client = mock<DaemonClient>()
        val response = sampleResponse()
        whenever(client.diagnoseModules()).thenReturn(response)

        val stdout = tapSystemOut { assertEquals(0, run(client, "--json")) }

        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    private fun run(client: DaemonClient, vararg args: String): Int =
        CommandLine(DiagnoseModulesCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .execute(*args)

    private fun sampleResponse() =
        ModulesDiagnosticsResponse(
            summary = ModuleLoadSummary(total = 2, loaded = 1, failed = 1),
            modules = listOf(
                ModuleLoadDiagnosticJson(module = "org.example.base", kind = "language", present = true, loaded = true),
                ModuleLoadDiagnosticJson(
                    module = "org.example.expr",
                    kind = "language",
                    present = true,
                    loaded = false,
                    problem = ModuleLoadProblemJson(
                        module = "org.example.expr",
                        reason = "BROKEN_DEPENDENCIES",
                        detail = "modules it depends on are not loaded",
                        causes = listOf(
                            ModuleLoadProblemJson(module = "jetbrains.mps.lang.editor.tooltips", reason = "ABSENT"),
                        ),
                    ),
                ),
            ),
        )

    private companion object {
        val nl: String = System.lineSeparator()
    }
}
