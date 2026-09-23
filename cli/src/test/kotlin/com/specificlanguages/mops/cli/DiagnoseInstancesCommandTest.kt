package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.specificlanguages.mops.cli.diagnose.DiagnoseInstancesCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.*
import picocli.CommandLine
import kotlin.test.*

@ResourceLock("system-streams")
class DiagnoseInstancesCommandTest {
    @Test
    fun `passes query options and scope and prints JSON`() {
        val client = mock<DaemonClient>()
        val filters = listOf(NodeFilter.Named("Json*"), NodeFilter.Role("members"))
        val scope = listOf("language", ".structure")
        val response = response().copy(scope = scope)
        whenever(client.diagnoseInstances("Concept", true, scope, filters, 3, "node-ref")).thenReturn(response)
        val stdout = tapSystemOut {
            assertEquals(0, CommandLine(DiagnoseInstancesCommand(client)).execute(
                "Concept", "--exact", "--named", "Json*", "--role", "members", "--limit", "3",
                "--expect", "node-ref", "--json", "in", "language", ".structure",
            ))
        }
        assertEquals(response, ProtocolJson.decodeResponse(stdout.trim()))
        verify(client).diagnoseInstances("Concept", true, scope, filters, 3, "node-ref")
    }

    @Test
    fun `text reports discrepancies incomplete probes and expected node status`() {
        val client = mock<DaemonClient>()
        whenever(client.diagnoseInstances("Concept", false, null, emptyList(), 100, null)).thenReturn(response())
        val stdout = tapSystemOut {
            assertEquals(0, CommandLine(DiagnoseInstancesCommand(client)).execute("Concept"))
        }
        assertContains(stdout, "complete=false")
        assertContains(stdout, "difference\tparticipants\tmissing=1\tunexpected=0")
        assertContains(stdout, "missing\tnode-ref")
        assertContains(stdout, "incomplete\tload failed")
        assertContains(stdout, "status=outside-scope")
    }

    @Test
    fun `rejects negative limits and malformed scope before daemon access`() {
        val client = mock<DaemonClient>()
        for (args in listOf(arrayOf("Concept", "--limit", "-1"), arrayOf("Concept", "in"), arrayOf("Concept", "wrong"))) {
            tapSystemErr {
                val exit = CommandLine(DiagnoseInstancesCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit).execute(*args)
                assertNotEquals(0, exit)
            }
        }
        verifyNoInteractions(client)
    }

    private fun response() = InstancesDiagnosticResponse(
        concept = InstanceConceptJson("Concept", "id", true), exact = false, scope = null,
        searchPath = "facade", mpsVersion = "2025.1.2", participants = listOf("Participant"),
        models = listOf(InstanceModelDiagnosticJson(
            model = "model-ref", implementation = "Model", source = "/model.mps", sourceType = "FileDataSource",
            streams = listOf("/model.mps"), readOnly = false, changed = false, loadedBefore = false,
            facadeCount = 0, lookupCount = 1, queryTreeCount = 1, semanticTreeCount = 1,
            differences = listOf(InstanceDifferenceJson("participants", 1, listOf("node-ref"), 0, emptyList())),
            errors = listOf("load failed"),
        )),
        normalCount = 0, filteredCount = 0, returnedCount = 0, limit = 100,
        expected = ExpectedInstanceJson("node-ref", true, inScope = false, status = "outside-scope"),
        errors = emptyList(), complete = false,
    )
}
