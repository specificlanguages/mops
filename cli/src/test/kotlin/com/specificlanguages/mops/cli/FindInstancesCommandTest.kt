package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeFilter
import com.specificlanguages.mops.protocol.NodeTarget
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
        whenever(client.findInstances(CONCEPT, false, limit = 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT)
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, limit = 100)
        assertEquals(
            "root\tJsonObject\tConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find instances renders unnamed nodes`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, limit = 100)).thenReturn(
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
            "root\t<unnamed>\tConceptDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find instances passes exact flag to the daemon`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, true, limit = 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--exact", CONCEPT)
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, true, limit = 100)
    }

    @Test
    fun `find instances passes the in-clause scope segments to the daemon`() {
        val client = mock<DaemonClient>()
        val scope = listOf("com.specificlanguages.json", ".structure")
        whenever(client.findInstances(CONCEPT, false, scope, limit = 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT, "in", "com.specificlanguages.json", ".structure")
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, scope, limit = 100)
    }

    @Test
    fun `find instances passes the named filter to the daemon`() {
        val client = mock<DaemonClient>()
        val filters = listOf(NodeFilter.Named("Json*"))
        whenever(client.findInstances(CONCEPT, false, filters = filters, limit = 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT, "--named", "Json*")
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, filters = filters, limit = 100)
    }

    @Test
    fun `find instances passes the role filter to the daemon`() {
        val client = mock<DaemonClient>()
        val filters = listOf(NodeFilter.Role("linkDeclaration"))
        whenever(client.findInstances(CONCEPT, false, filters = filters, limit = 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT, "--role", "linkDeclaration")
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, filters = filters, limit = 100)
    }

    @Test
    fun `find instances combines named, role, and scope in one query`() {
        val client = mock<DaemonClient>()
        val scope = listOf("com.specificlanguages.json", ".structure")
        val filters = listOf(NodeFilter.Named("Json*"), NodeFilter.Role("linkDeclaration"))
        whenever(client.findInstances(CONCEPT, false, scope, filters, 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(
                    CONCEPT,
                    "--named",
                    "Json*",
                    "--role",
                    "linkDeclaration",
                    "in",
                    "com.specificlanguages.json",
                    ".structure",
                )
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, scope, filters, 100)
    }

    @Test
    fun `find instances maps in slash to the repository scope`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, listOf("/"), limit = 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(CONCEPT, "in", "/")
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, listOf("/"), limit = 100)
    }

    @Test
    fun `find instances keeps a query literally named in as the concept`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances("in", false, listOf("/"), limit = 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("in", "in", "/")
        }

        assertEquals(0, exitCode)
        verify(client).findInstances("in", false, listOf("/"), limit = 100)
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
        whenever(client.findInstances(CONCEPT, false, limit = 100)).thenReturn(response)
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
        whenever(client.findInstances(CONCEPT, false, limit = 0)).thenReturn(sampleInstancesResponse(limit = 0))
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "0", CONCEPT)
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, limit = 0)
    }

    @Test
    fun `find instances appends a truncation row when more results exist`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, limit = 1)).thenReturn(
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
            "root\tJsonObject\tConceptDeclaration\t" +
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
        whenever(client.findInstances(CONCEPT, false, limit = 100)).thenReturn(
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
            "node\tcontent\tLinkDeclaration\t" +
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905\t" +
                "parent\tJsonObject\tConceptDeclaration\t" +
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

    @Test
    fun `find instances shows fully qualified concept names with --full-concept`() {
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
        whenever(client.findInstances(CONCEPT, false, limit = 100)).thenReturn(
            FindInstancesResponse(limit = 100, truncated = false, nodes = listOf(node)),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--full-concept", CONCEPT)
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
    fun `find instances prints one node reference per line with --refs-only`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, limit = 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--refs-only", CONCEPT)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find instances combines --refs-only with scope and filters`() {
        val client = mock<DaemonClient>()
        val scope = listOf("com.specificlanguages.json", ".structure")
        val filters = listOf(NodeFilter.Named("Json*"), NodeFilter.Role("linkDeclaration"))
        whenever(client.findInstances(CONCEPT, false, scope, filters, 100)).thenReturn(sampleInstancesResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(
                    "--refs-only",
                    CONCEPT,
                    "--named",
                    "Json*",
                    "--role",
                    "linkDeclaration",
                    "in",
                    "com.specificlanguages.json",
                    ".structure",
                )
        }

        assertEquals(0, exitCode)
        verify(client).findInstances(CONCEPT, false, scope, filters, 100)
        assertEquals(
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find instances rejects --refs-only combined with --json`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--refs-only", "--json", CONCEPT)
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find instances reports --refs-only truncation on stderr keeping stdout pipe-clean`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, false, limit = 1)).thenReturn(
            sampleInstancesResponse(limit = 1).copy(truncated = true),
        )
        var exitCode = Int.MIN_VALUE
        var stdout = ""

        val stderr = tapSystemErr {
            stdout = tapSystemOut {
                exitCode = CommandLine(FindInstancesCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute("--refs-only", "--limit", "1", CONCEPT)
            }
        }

        assertEquals(0, exitCode)
        assertEquals(
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905" +
                System.lineSeparator(),
            stdout,
        )
        assertEquals("truncated\t1\tmore results not shown" + System.lineSeparator(), stderr)
    }

    @Test
    fun `refs-only output feeds back into model get-node`() {
        val findClient = mock<DaemonClient>()
        whenever(findClient.findInstances(CONCEPT, false, limit = 100)).thenReturn(sampleInstancesResponse())
        var stdout = ""

        tapSystemOut {
            CommandLine(FindInstancesCommand(findClient))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--refs-only", CONCEPT)
        }.also { stdout = it }

        val reference = stdout.trim()
        val getNodeClient = mock<DaemonClient>()
        val target = NodeTarget.NodeReference(reference)
        whenever(getNodeClient.getNode(target)).thenReturn(
            ModelGetNodeResponse(
                node = MpsNodeJson(
                    model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    id = "2110045694544566905",
                ),
            ),
        )
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(ModelGetNodeCommand(getNodeClient))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(reference)
        }

        assertEquals(0, exitCode)
        verify(getNodeClient).getNode(target)
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
