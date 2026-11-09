package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.find.FindRootByNameCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.NamePatternTargetJson
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine

/** Tests what `find root-by-name` selects. [SearchCommandTest] tests the output, `--limit`, and the scope clause. */
@ResourceLock("system-streams")
class FindRootByNameCommandTest {
    @Test
    fun `find root-by-name names the pattern and scope when nothing matches`() {
        val client = mock<DaemonClient>()
        val model = SearchScopeJson(
            kind = SearchScopeKind.MODEL,
            reference = TEST_MODEL,
            name = "com.specificlanguages.json.structure",
            models = 1,
        )
        whenever(client.findByName("Nope*", listOf("com.specificlanguages.json.structure"), limit = 100)).thenReturn(
            byNameResponse(nodes = emptyList(), scope = model, target = NamePatternTargetJson("Nope*")),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(FindRootByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("Nope*", "in", "com.specificlanguages.json.structure")
        }

        assertEquals(0, exitCode)
        assertEquals(
            "# no root nodes matching Nope* in model com.specificlanguages.json.structure (searched 1 model)" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find root-by-name rejects a blank pattern before dispatching to the daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(FindRootByNameCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("   ")
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }
}
