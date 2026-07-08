package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModuleDiagnosticResponse
import com.specificlanguages.mops.protocol.ModuleLoadDiagnosticJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class DiagnoseModuleCommandTest {
    @Test
    fun `diagnose module prints the header and the full problem tree`() {
        val client = mock<DaemonClient>()
        whenever(client.diagnoseModule(MODULE)).thenReturn(
            ModuleDiagnosticResponse(
                module = ModuleLoadDiagnosticJson(
                    module = MODULE,
                    kind = "language",
                    present = true,
                    loaded = false,
                    problem = ModuleLoadProblemJson(
                        module = MODULE,
                        reason = "BROKEN_DEPENDENCIES",
                        detail = "modules it depends on are not loaded",
                        causes = listOf(
                            ModuleLoadProblemJson(
                                module = "com.mbeddr.core.base",
                                reason = "BROKEN_DEPENDENCIES",
                                detail = "modules it depends on are not loaded",
                                causes = listOf(
                                    ModuleLoadProblemJson(
                                        module = "jetbrains.mps.lang.editor.tooltips",
                                        reason = "ABSENT",
                                        detail = "not present in the repository",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val stdout = tapSystemOut {
            assertEquals(0, run(client, MODULE))
        }

        assertEquals(
            "org.iets3.core.expr.base\tlanguage\tpresent=true\tloaded=false" + nl +
                "\torg.iets3.core.expr.base\tBROKEN_DEPENDENCIES\tmodules it depends on are not loaded" + nl +
                "\t\tcom.mbeddr.core.base\tBROKEN_DEPENDENCIES\tmodules it depends on are not loaded" + nl +
                "\t\t\tjetbrains.mps.lang.editor.tooltips\tABSENT\tnot present in the repository" + nl,
            stdout,
        )
    }

    @Test
    fun `diagnose module reports an absent module`() {
        val client = mock<DaemonClient>()
        whenever(client.diagnoseModule("no.such.module")).thenReturn(
            ModuleDiagnosticResponse(
                module = ModuleLoadDiagnosticJson(
                    module = "no.such.module",
                    kind = "unknown",
                    present = false,
                    loaded = false,
                    problem = ModuleLoadProblemJson(module = "no.such.module", reason = "ABSENT", detail = "not present in the repository"),
                ),
            ),
        )

        val stdout = tapSystemOut { assertEquals(0, run(client, "no.such.module")) }

        assertEquals(
            "no.such.module\tunknown\tpresent=false\tloaded=false" + nl +
                "\tno.such.module\tABSENT\tnot present in the repository" + nl,
            stdout,
        )
    }

    @Test
    fun `diagnose module passes the module argument to the daemon and can emit json`() {
        val client = mock<DaemonClient>()
        val response = ModuleDiagnosticResponse(
            module = ModuleLoadDiagnosticJson(module = MODULE, kind = "language", present = true, loaded = true),
        )
        whenever(client.diagnoseModule(MODULE)).thenReturn(response)

        val stdout = tapSystemOut { assertEquals(0, run(client, "--json", MODULE)) }

        verify(client).diagnoseModule(MODULE)
        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    private fun run(client: DaemonClient, vararg args: String): Int =
        CommandLine(DiagnoseModuleCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .execute(*args)

    private companion object {
        const val MODULE = "org.iets3.core.expr.base"
        val nl: String = System.lineSeparator()
    }
}
