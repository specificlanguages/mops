package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.find.FindUsagesCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

/** Tests what `find usages` selects. [SearchCommandTest] tests the output, `--limit`, and the scope clause. */
@ResourceLock("system-streams")
class FindUsagesCommandTest {
    @Test
    fun `find usages names a subtree scope by its node without a model count`() {
        val client = mock<DaemonClient>()
        val subtree = SearchScopeJson(kind = SearchScopeKind.SUBTREE, reference = testNode.reference, name = "JsonObject")
        val target = MpsNodeSummaryJson(
            type = "root",
            name = "IJsonValue",
            concept = "jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration",
            reference = TARGET_REFERENCE,
        )
        whenever(client.findUsages(NodeTarget.NodeReference(TARGET_REFERENCE), listOf("JsonObject"), limit = 100))
            .thenReturn(usagesResponse(owners = emptyList(), scope = subtree, target = target))

        val stdout = execute(client, "--full-concept", TARGET_REFERENCE, "in", "JsonObject")

        assertEquals(
            "# no usages of IJsonValue (jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration) in " +
                "the subtree of JsonObject ${testNode.reference}" + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `find usages accepts a model target and node ID`() {
        val client = mock<DaemonClient>()
        whenever(client.findUsages(IN_MODEL_TARGET, limit = 100)).thenReturn(usagesResponse())

        execute(client, "com.specificlanguages.json.structure", "2110045694544566904")

        verify(client).findUsages(IN_MODEL_TARGET, limit = 100)
    }

    @Test
    fun `find usages passes the in-clause scope after a model target and node ID`() {
        val client = mock<DaemonClient>()
        val scope = listOf("com.specificlanguages.json")
        whenever(client.findUsages(IN_MODEL_TARGET, scope, limit = 100)).thenReturn(usagesResponse())

        execute(client, "com.specificlanguages.json.structure", "2110045694544566904", "in", "com.specificlanguages.json")

        verify(client).findUsages(IN_MODEL_TARGET, scope, limit = 100)
    }

    /** Runs `find usages` with [args], checks that it succeeded, and returns its stdout. */
    private fun execute(client: DaemonClient, vararg args: String): String {
        var exitCode = Int.MIN_VALUE
        val stdout = tapSystemOut {
            exitCode = CommandLine(FindUsagesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(*args)
        }
        assertEquals(0, exitCode, stdout)
        return stdout
    }

    private companion object {
        const val TARGET_REFERENCE = "$TEST_MODEL/2110045694544566909"
        val IN_MODEL_TARGET = NodeTarget.InModel(
            modelTarget = "com.specificlanguages.json.structure",
            nodeId = "2110045694544566904",
        )
    }
}
