package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.find.FindNodeByIdCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindNodeByIdResponse
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.NodeIdTargetJson
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class FindNodeByIdCommandTest {
    @Test
    fun `find node-by-id names the id and scope when no node carries it`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ENCODED_ID, listOf("/"), limit = 100)).thenReturn(
            FindNodeByIdResponse(
                target = NodeIdTargetJson(ID),
                scope = SearchScopeJson(SearchScopeKind.REPOSITORY, models = 900),
                limit = 100,
                truncated = false,
                nodes = emptyList(),
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(ENCODED_ID, "in", "/")
        }

        assertEquals(0, exitCode)
        assertEquals("# no nodes with ID $ID in the repository (searched 900 models)" + System.lineSeparator(), stdout)
    }

    @Test
    fun `find node-by-id prints tab-separated rows for an id`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ID, limit = 100)).thenReturn(sampleResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(ID)
        }

        assertEquals(0, exitCode)
        verify(client).findNodeById(ID, limit = 100)
        assertEquals(
            "root\tJsonObject\tConceptDeclaration\t$REFERENCE" + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find node-by-id renders unnamed nodes`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ID, limit = 100)).thenReturn(sampleResponse(name = null))
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(ID)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "root\t<unnamed>\tConceptDeclaration\t$REFERENCE" + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find node-by-id accepts an encoded-spelling id and passes it through verbatim`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ENCODED_ID, limit = 100)).thenReturn(sampleResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(ENCODED_ID)
        }

        assertEquals(0, exitCode)
        verify(client).findNodeById(ENCODED_ID, limit = 100)
    }

    @Test
    fun `find node-by-id passes the scope clause to the daemon`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ID, listOf("com.specificlanguages.json"), limit = 100)).thenReturn(sampleResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(ID, "in", "com.specificlanguages.json")
        }

        assertEquals(0, exitCode)
        verify(client).findNodeById(ID, listOf("com.specificlanguages.json"), limit = 100)
    }

    @Test
    fun `find node-by-id passes the repository scope to the daemon`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ID, listOf("/"), limit = 100)).thenReturn(sampleResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(ID, "in", "/")
        }

        assertEquals(0, exitCode)
        verify(client).findNodeById(ID, listOf("/"), limit = 100)
    }

    @Test
    fun `find node-by-id rejects a bare in with no scope segments`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(ID, "in")
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find node-by-id says how it read a model and an id given without in`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("com.specificlanguages.json.structure", ID)
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
        assertEquals(
            "mops read `com.specificlanguages.json.structure` as ID and `$ID` as a scope clause, " +
                "but a scope clause opens with `in`: write `ID in SCOPE_SEGMENT...`; see: mops explain scope" +
                System.lineSeparator(),
            stderr,
        )
    }

    @Test
    fun `find node-by-id prints response object as json when requested`() {
        val client = mock<DaemonClient>()
        val response = sampleResponse()
        whenever(client.findNodeById(ID, limit = 100)).thenReturn(response)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--json", ID)
        }

        assertEquals(0, exitCode)
        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    @Test
    fun `find node-by-id appends a metadata line naming --limit when more results exist`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ID, limit = 1)).thenReturn(
            sampleResponse(limit = 1).copy(truncated = true),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "1", ID)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "root\tJsonObject\tConceptDeclaration\t$REFERENCE" + System.lineSeparator() +
                "# truncated: showing 1; pass --limit 0 for all" + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find node-by-id rejects a blank id before dispatching to the daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("   ")
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find node-by-id rejects a negative limit before dispatching to the daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "-1", ID)
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `find node-by-id shows fully qualified concept names with --full-concept`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ID, limit = 100)).thenReturn(sampleResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--full-concept", ID)
        }

        assertEquals(0, exitCode)
        assertEquals(
            "root\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t$REFERENCE" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find node-by-id prints one node reference per line with --refs-only`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ID, limit = 100)).thenReturn(sampleResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--refs-only", ID)
        }

        assertEquals(0, exitCode)
        assertEquals(REFERENCE + System.lineSeparator(), stdout)
    }

    @Test
    fun `find node-by-id rejects --refs-only combined with --json`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindNodeByIdCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--refs-only", "--json", ID)
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    private fun sampleResponse(limit: Int = 100, name: String? = "JsonObject") =
        FindNodeByIdResponse(
            target = testNodeIdTarget,
            scope = testScope,
            limit = limit,
            truncated = false,
            nodes = listOf(
                MpsNodeSummaryJson(
                    type = "root",
                    name = name,
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    reference = REFERENCE,
                ),
            ),
        )

    private companion object {
        const val ID = "2110045694544566904"
        // The same Node ID in the encoded spelling MPS persists; the command must pass it through untouched.
        const val ENCODED_ID = "1P8oQ4NaXDS"
        const val REFERENCE =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
    }
}
