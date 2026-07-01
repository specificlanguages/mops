package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeUsageJson
import com.specificlanguages.mops.protocol.NodeTarget
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class FindUsagesCommandTest {
    @Test
    fun `find usages prints tab-separated rows for a node reference`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), 100)).thenReturn(sampleUsagesResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(nodeReference)
        }

        assertEquals(0, exitCode)
        verify(client).findUsages(NodeTarget.NodeReference(nodeReference), 100)
        assertEquals(
            "usage\tconcept\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find usages accepts a model target and node id`() {
        val client = mock<DaemonClient>()
        val target = NodeTarget.InModel(
            modelTarget = "com.specificlanguages.json.structure",
            nodeId = "2110045694544566904",
        )
        whenever(client.findUsages(target, 100)).thenReturn(sampleUsagesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("com.specificlanguages.json.structure", "2110045694544566904")
        }

        assertEquals(0, exitCode)
        verify(client).findUsages(target, 100)
    }

    @Test
    fun `find usages prints response object as json when requested`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        val response = sampleUsagesResponse()
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), 100)).thenReturn(response)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--json", nodeReference)
        }

        assertEquals(0, exitCode)
        assertEquals(response, GsonCodec.fromJson(stdout, FindUsagesResponse::class.java))
    }

    @Test
    fun `find usages accepts zero as unlimited limit`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), 0)).thenReturn(
            sampleUsagesResponse(limit = 0),
        )
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "0", nodeReference)
        }

        assertEquals(0, exitCode)
        verify(client).findUsages(NodeTarget.NodeReference(nodeReference), 0)
    }

    @Test
    fun `find usages appends a truncation row when more results exist`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), 1)).thenReturn(
            sampleUsagesResponse(limit = 1).copy(truncated = true),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "1", nodeReference)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "usage\tconcept\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator() +
                "truncated\t1\tmore results not shown" + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find usages rejects a negative limit before dispatching to the daemon`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "-1", nodeReference)
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    private fun sampleUsagesResponse(limit: Int = 100) =
        FindUsagesResponse(
            limit = limit,
            truncated = false,
            usages = listOf(
                MpsNodeUsageJson(
                    role = "concept",
                    owner = MpsNodeSummaryJson(
                        type = "node",
                        name = "JsonObject",
                        concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                        reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905",
                    ),
                ),
            ),
        )
}
