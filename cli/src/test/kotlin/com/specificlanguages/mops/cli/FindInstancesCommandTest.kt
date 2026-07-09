package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsNodeParentJson
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
class FindInstancesCommandTest {
    @Test
    fun `find instances prints tab-separated rows for a concept`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT)
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, 100)
        assertEquals(
            "root\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find instances renders unnamed nodes`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, 100)).thenReturn(
            sampleInstancesResponse(name = null),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT)
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
    fun `find instances passes exact flag to the daemon`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, true, 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--exact", CONCEPT)
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, true, 100)
    }

    @Test
    fun `find instances passes the in-clause scope segments to the daemon`() {
        val client = mock<DaemonClient>()
        val scope = listOf("com.specificlanguages.json", ".structure")
        whenever(client.findInstances(CONCEPT, false, 100, scope)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT, "in", "com.specificlanguages.json", ".structure")
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, 100, scope)
    }

    @Test
    fun `find instances maps in slash to the repository scope`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, 100, listOf("/"))).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT, "in", "/")
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, 100, listOf("/"))
    }

    @Test
    fun `find instances keeps a query literally named in as the concept`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances("in", false, 100, listOf("/"))).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("in", "in", "/")
        }

        assertEquals(0, exitCode)
        verify(client).findInstances("in", false, 100, listOf("/"))
    }

    @Test
    fun `find instances rejects an in clause with no scope segments`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT, "in")
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find instances rejects the removed all option`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--all", CONCEPT)
        }

        assertEquals(2, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find instances prints response object as json when requested`() {
        val client = mock<DaemonClient>()
        val response = sampleInstancesResponse()
        whenever(client.findInstances(CONCEPT, false, 100)).thenReturn(response)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--json", CONCEPT)
        }

        assertEquals(0, exitCode)
        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    @Test
    fun `find instances accepts zero as unlimited limit`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, 0)).thenReturn(sampleInstancesResponse(limit = 0))
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "0", CONCEPT)
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, 0)
    }

    @Test
    fun `find instances appends a truncation row when more results exist`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, 1)).thenReturn(
            sampleInstancesResponse(limit = 1).copy(truncated = true),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "1", CONCEPT)
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
    fun `find instances appends parent columns when the node has a parent`() {
        val client = mock<DaemonClient>()
        val node = MpsNodeSummaryJson(
            type = "node",
            name = "content",
            concept = "jetbrains.mps.lang.structure.structure.LinkDeclaration",
            reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905",
            parent = MpsNodeParentJson(
                type = "root",
                role = "linkDeclaration",
                name = "JsonObject",
                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566800",
            ),
        )
        whenever(client.findInstances(CONCEPT, false, 100)).thenReturn(
            FindInstancesResponse(limit = 100, truncated = false, nodes = listOf(node)),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "node\tcontent\tjetbrains.mps.lang.structure.structure.LinkDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905\t" +
                "parent\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566800" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find instances rejects a negative limit before dispatching to the daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "-1", CONCEPT)
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    private fun sampleInstancesResponse(limit: Int = 100, name: String? = "JsonObject") =
        FindInstancesResponse(
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
        const val CONCEPT = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
    }
}
