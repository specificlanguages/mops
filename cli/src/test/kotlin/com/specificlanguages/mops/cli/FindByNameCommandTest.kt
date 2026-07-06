package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class FindByNameCommandTest {
    @Test
    fun `find by-name prints tab-separated rows for a pattern`() {
        val client = mock<DaemonClient>()
        whenever(client.findByName(PATTERN, 100, false)).thenReturn(sampleResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(PATTERN)
        }

        assertEquals(0, exitCode)
        verify(client).findByName(PATTERN, 100, false)
        assertEquals(
            "root\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find by-name renders unnamed nodes`() {
        val client = mock<DaemonClient>()
        whenever(client.findByName(PATTERN, 100, false)).thenReturn(sampleResponse(name = null))
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(PATTERN)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "root\t<unnamed>\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find by-name passes all flag to the daemon`() {
        val client = mock<DaemonClient>()
        whenever(client.findByName(PATTERN, 100, true)).thenReturn(sampleResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--all", PATTERN)
        }

        assertEquals(0, exitCode)
        verify(client).findByName(PATTERN, 100, true)
    }

    @Test
    fun `find by-name prints response object as json when requested`() {
        val client = mock<DaemonClient>()
        val response = sampleResponse()
        whenever(client.findByName(PATTERN, 100, false)).thenReturn(response)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--json", PATTERN)
        }

        assertEquals(0, exitCode)
        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    @Test
    fun `find by-name appends a truncation row when more results exist`() {
        val client = mock<DaemonClient>()
        whenever(client.findByName(PATTERN, 1, false)).thenReturn(
            sampleResponse(limit = 1).copy(truncated = true),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "1", PATTERN)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "root\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator() +
                "truncated\t1\tmore results not shown" + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find by-name rejects a blank pattern before dispatching to the daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("   ")
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find by-name rejects a negative limit before dispatching to the daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "-1", PATTERN)
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    private fun sampleResponse(limit: Int = 100, name: String? = "JsonObject") =
        FindByNameResponse(
            limit = limit,
            truncated = false,
            nodes = listOf(
                MpsNodeSummaryJson(
                    type = "root",
                    name = name,
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905",
                ),
            ),
        )

    private companion object {
        const val PATTERN = "Json"
    }
}
