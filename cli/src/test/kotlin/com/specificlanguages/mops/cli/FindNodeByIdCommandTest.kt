package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.find.FindNodeByIdCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NodeIdTargetJson
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

/** Tests what `find node-by-id` selects. [SearchCommandTest] tests the output, `--limit`, and the scope clause. */
@ResourceLock("system-streams")
class FindNodeByIdCommandTest {
    @Test
    fun `find node-by-id names the decimal id and scope when no node carries it`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ENCODED_ID, listOf("/"), limit = 100)).thenReturn(
            nodeIdResponse(
                nodes = emptyList(),
                scope = SearchScopeJson(SearchScopeKind.REPOSITORY, models = 900),
                target = NodeIdTargetJson(ID),
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
    fun `find node-by-id accepts an encoded-spelling id and passes it through verbatim`() {
        val client = mock<DaemonClient>()
        whenever(client.findNodeById(ENCODED_ID, limit = 100)).thenReturn(nodeIdResponse())
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

    private companion object {
        const val ID = "2110045694544566904"
        // The same Node ID in the encoded spelling MPS persists; the command must pass it through untouched.
        const val ENCODED_ID = "1P8oQ4NaXDS"
    }
}
