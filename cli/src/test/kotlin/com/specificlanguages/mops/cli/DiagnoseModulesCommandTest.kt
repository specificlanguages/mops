package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.diagnose.DiagnoseModulesCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModuleLoadDiagnosticJson
import com.specificlanguages.mops.protocol.ModuleLoadFix
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemReason
import com.specificlanguages.mops.protocol.ModuleLoadSummary
import com.specificlanguages.mops.protocol.ModulesDiagnosticsResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class DiagnoseModulesCommandTest {
    @Test
    fun `diagnose modules prints the summary, failed rows with flattened root causes, and a # metadata line`() {
        val client = mock<DaemonClient>()
        whenever(client.diagnoseModules()).thenReturn(sampleResponse())

        val stdout = tapSystemOut { assertEquals(0, run(client)) }

        assertEquals(
            "modules\t1/2 loaded\t1 failed" + nl +
                "org.example.expr\tlanguage\tBROKEN_DEPENDENCIES" + nl +
                "\tjetbrains.mps.lang.editor.tooltips\tABSENT" + nl +
                "# modules without a Java facet are not listed; diagnose one with: mops diagnose module <module>" + nl,
            stdout,
        )
    }

    @Test
    fun `root cause rows end with their fix`() {
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
                        problem = ModuleLoadProblemJson(
                            module = "org.example.base",
                            reason = ModuleLoadProblemReason.BROKEN_DEPENDENCIES,
                            causes = listOf(
                                ModuleLoadProblemJson(
                                    module = "org.example.dep",
                                    reason = ModuleLoadProblemReason.NOT_BUILT,
                                    fix = ModuleLoadFix(listOf("org.example.dep")),
                                ),
                                ModuleLoadProblemJson(module = "org.example.gone", reason = ModuleLoadProblemReason.ABSENT),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val stdout = tapSystemOut { assertEquals(0, run(client)) }

        assertEquals(
            "modules\t0/1 loaded\t1 failed" + nl +
                "org.example.base\tlanguage\tBROKEN_DEPENDENCIES" + nl +
                "\torg.example.dep\tNOT_BUILT\tfix=mops make module org.example.dep" + nl +
                "\torg.example.gone\tABSENT" + nl +
                "# modules without a Java facet are not listed; diagnose one with: mops diagnose module <module>" + nl,
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
                "# modules without a Java facet are not listed; diagnose one with: mops diagnose module <module>" + nl,
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
                        problem = ModuleLoadProblemJson(
                            module = "org.example.base",
                            reason = ModuleLoadProblemReason.NOT_BUILT,
                            detail = "not built",
                            fix = ModuleLoadFix(listOf("org.example.base")),
                        ),
                    ),
                ),
            ),
        )

        val stdout = tapSystemOut { assertEquals(0, run(client)) }

        assertEquals(
            "modules\t0/1 loaded\t1 failed" + nl +
                "org.example.base\tlanguage\tNOT_BUILT\tfix=mops make module org.example.base" + nl +
                "# modules without a Java facet are not listed; diagnose one with: mops diagnose module <module>" + nl,
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
                        reason = ModuleLoadProblemReason.BROKEN_DEPENDENCIES,
                        detail = "modules it depends on are not loaded",
                        causes = listOf(
                            ModuleLoadProblemJson(module = "jetbrains.mps.lang.editor.tooltips", reason = ModuleLoadProblemReason.ABSENT),
                        ),
                    ),
                ),
            ),
        )

    private companion object {
        val nl: String = System.lineSeparator()
    }
}
