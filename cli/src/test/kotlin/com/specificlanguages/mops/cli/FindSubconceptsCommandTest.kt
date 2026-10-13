package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.find.FindSubconceptsCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindSubconceptsResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class FindSubconceptsCommandTest {
    @Test
    fun `find subconcepts prints qualified names and forwards hierarchy filters`() {
        val client = mock<DaemonClient>()
        whenever(client.findSubconcepts("Expression", true, true)).thenReturn(
            FindSubconceptsResponse(listOf("example.structure.ConcreteExpression")),
        )
        val stdout = tapSystemOut {
            assertEquals(0, CommandLine(FindSubconceptsCommand(client)).execute("Expression", "--concrete", "--direct"))
        }
        assertEquals("example.structure.ConcreteExpression" + System.lineSeparator(), stdout)
        verify(client).findSubconcepts("Expression", true, true)
    }
    @Test
    fun `find subconcepts defaults to all descendants and renders empty JSON results`() {
        val client = mock<DaemonClient>()
        val response = FindSubconceptsResponse(emptyList())
        whenever(client.findSubconcepts("Expression", false, false)).thenReturn(response)
        val stdout = tapSystemOut {
            assertEquals(0, CommandLine(FindSubconceptsCommand(client)).execute("Expression", "--json"))
        }
        assertEquals(response, ProtocolJson.decodeResponse(stdout))
        verify(client).findSubconcepts("Expression", false, false)
    }

}
