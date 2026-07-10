package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsNodeParentJson
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
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), limit = 100)).thenReturn(sampleUsagesResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(nodeReference)
        }

        assertEquals(0, exitCode)
        verify(client).findUsages(NodeTarget.NodeReference(nodeReference), limit = 100)
        assertEquals(
            "usage\tconcept\tJsonObject\tConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find usages appends parent columns when the owner has a parent`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        val owner = MpsNodeSummaryJson(
            type = "node",
            name = "JsonObject",
            concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
            reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905",
            parent = MpsNodeParentJson(
                type = "root",
                role = "members",
                name = "JsonFile",
                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566800",
            ),
        )
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), limit = 100)).thenReturn(
            FindUsagesResponse(limit = 100, truncated = false, usages = listOf(MpsNodeUsageJson(role = "concept", owner = owner))),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(nodeReference)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "usage\tconcept\tJsonObject\tConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905\t" +
                "parent\tJsonFile\tConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566800" +
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
        whenever(client.findUsages(target, limit = 100)).thenReturn(sampleUsagesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("com.specificlanguages.json.structure", "2110045694544566904")
        }

        assertEquals(0, exitCode)
        verify(client).findUsages(target, limit = 100)
    }

    @Test
    fun `find usages passes the in-clause scope after a node reference`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), listOf("/"), limit = 100))
            .thenReturn(sampleUsagesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(nodeReference, "in", "/")
        }

        assertEquals(0, exitCode)
        verify(client).findUsages(NodeTarget.NodeReference(nodeReference), listOf("/"), limit = 100)
    }

    @Test
    fun `find usages passes the in-clause scope after a model target and node id`() {
        val client = mock<DaemonClient>()
        val target = NodeTarget.InModel(
            modelTarget = "com.specificlanguages.json.structure",
            nodeId = "2110045694544566904",
        )
        val scope = listOf("com.specificlanguages.json")
        whenever(client.findUsages(target, scope, limit = 100)).thenReturn(sampleUsagesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("com.specificlanguages.json.structure", "2110045694544566904", "in", "com.specificlanguages.json")
        }

        assertEquals(0, exitCode)
        verify(client).findUsages(target, scope, limit = 100)
    }

    @Test
    fun `find usages rejects an in clause with no scope segments`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(nodeReference, "in")
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find usages rejects the removed all option`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--all", nodeReference)
        }

        assertEquals(2, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find usages prints response object as json when requested`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        val response = sampleUsagesResponse()
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), limit = 100)).thenReturn(response)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--json", nodeReference)
        }

        assertEquals(0, exitCode)
        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    @Test
    fun `find usages accepts zero as unlimited limit`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), limit = 0)).thenReturn(
            sampleUsagesResponse(limit = 0),
        )
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "0", nodeReference)
        }

        assertEquals(0, exitCode)
        verify(client).findUsages(NodeTarget.NodeReference(nodeReference), limit = 0)
    }

    @Test
    fun `find usages appends a truncation row when more results exist`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), limit = 1)).thenReturn(
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
            "usage\tconcept\tJsonObject\tConceptDeclaration\t" +
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

    @Test
    fun `find usages shows fully qualified concept names with --full-concept`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), limit = 100)).thenReturn(sampleUsagesResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--full-concept", nodeReference)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "usage\tconcept\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find usages prints the referencing node reference per line with --refs-only`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        whenever(client.findUsages(NodeTarget.NodeReference(nodeReference), limit = 100)).thenReturn(sampleUsagesResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--refs-only", nodeReference)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find usages rejects --refs-only combined with --json`() {
        val client = mock<DaemonClient>()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--refs-only", "--json", nodeReference)
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
