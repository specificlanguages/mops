package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateLanguageCommandTest {
    @Test
    fun `language creation forwards all options and renders the report`() {
        val client = mock<DaemonClient>()
        val report = ModuleCreationReport(
            ModuleCreationEntry("l:1(example.lang)", "example.lang", ModuleKind.LANGUAGE, "/p/custom.mpl"),
        )
        whenever(client.createLanguage("example.lang", "custom.mpl", true, false))
            .thenReturn(ModuleCreationResponse(report = report))

        val output = tapSystemOut {
            assertEquals(0, CommandLine(CreateLanguageCommand(client)).execute(
                "example.lang", "--descriptor", "custom.mpl", "--with-generator",
            ))
        }

        verify(client).createLanguage("example.lang", "custom.mpl", true, false)
        assertEquals("created language example.lang [l:1(example.lang)] at /p/custom.mpl\n", output)
    }

    @Test
    fun `dry run json prints a plan`() {
        val client = mock<DaemonClient>()
        val plan = ModuleCreationPlan(
            ModuleCreationPlanEntry("example.lang", ModuleKind.LANGUAGE, "/p/languages/example.lang/example.lang.mpl"),
        )
        whenever(client.createLanguage("example.lang", null, false, true))
            .thenReturn(ModuleCreationResponse(plan = plan))

        val output = tapSystemOut {
            assertEquals(0, CommandLine(CreateLanguageCommand(client)).execute("example.lang", "--dry-run", "--json"))
        }

        assertEquals(ProtocolJson.encodeModuleCreationPlan(plan) + "\n", output)
    }
}
