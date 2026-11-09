package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.find.FindInstancesCommand
import com.specificlanguages.mops.cli.get.ModelGetNodeCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ConceptTargetJson
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeFilter
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

/** Tests what `find instances` selects. [SearchCommandTest] tests the output, `--limit`, and the scope clause. */
@ResourceLock("system-streams")
class FindInstancesCommandTest {
    @Test
    fun `find instances names the concept, filters, and scope when nothing matches`() {
        val client = mock<DaemonClient>()
        val filters = listOf(NodeFilter.Named("Foo*"), NodeFilter.Role("members"))
        val module = SearchScopeJson(
            kind = SearchScopeKind.MODULE,
            reference = "fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json)",
            name = "com.specificlanguages.json",
            models = 3,
        )
        whenever(
            client.findInstances(
                "ConceptDeclaration",
                true,
                scope = listOf("com.specificlanguages.json"),
                filters = filters,
                limit = 100,
            ),
        ).thenReturn(instancesResponse(nodes = emptyList(), scope = module, target = ConceptTargetJson(CONCEPT)))
        var stdout = ""

        val stderr = tapSystemErr {
            stdout = execute(
                client,
                "--exact",
                "--named",
                "Foo*",
                "--role",
                "members",
                "ConceptDeclaration",
                "in",
                "com.specificlanguages.json",
            )
        }

        assertEquals(
            "# no exact instances of $CONCEPT matching --named Foo* --role members in module " +
                "com.specificlanguages.json (searched 3 models)" + System.lineSeparator(),
            stdout,
        )
        assertEquals("", stderr)
    }

    @Test
    fun `find instances passes exact flag to the daemon`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances(CONCEPT, true, limit = 100)).thenReturn(instancesResponse())

        execute(client, "--exact", CONCEPT)

        verify(client).findInstances(CONCEPT, true, limit = 100)
    }

    @Test
    fun `find instances passes the named filter to the daemon`() {
        val client = mock<DaemonClient>()
        val filters = listOf(NodeFilter.Named("Json*"))
        whenever(client.findInstances(CONCEPT, false, filters = filters, limit = 100)).thenReturn(instancesResponse())

        execute(client, CONCEPT, "--named", "Json*")

        verify(client).findInstances(CONCEPT, false, filters = filters, limit = 100)
    }

    @Test
    fun `find instances passes the role filter to the daemon`() {
        val client = mock<DaemonClient>()
        val filters = listOf(NodeFilter.Role("linkDeclaration"))
        whenever(client.findInstances(CONCEPT, false, filters = filters, limit = 100)).thenReturn(instancesResponse())

        execute(client, CONCEPT, "--role", "linkDeclaration")

        verify(client).findInstances(CONCEPT, false, filters = filters, limit = 100)
    }

    @Test
    fun `find instances combines named, role, and scope in one query`() {
        val client = mock<DaemonClient>()
        val scope = listOf("com.specificlanguages.json", ".structure")
        val filters = listOf(NodeFilter.Named("Json*"), NodeFilter.Role("linkDeclaration"))
        whenever(client.findInstances(CONCEPT, false, scope, filters, 100)).thenReturn(instancesResponse())

        execute(
            client,
            CONCEPT,
            "--named",
            "Json*",
            "--role",
            "linkDeclaration",
            "in",
            "com.specificlanguages.json",
            ".structure",
        )

        verify(client).findInstances(CONCEPT, false, scope, filters, 100)
    }

    @Test
    fun `find instances keeps a query literally named in as the concept`() {
        val client = mock<DaemonClient>()
        whenever(client.findInstances("in", false, listOf("/"), limit = 100)).thenReturn(instancesResponse())

        execute(client, "in", "in", "/")

        verify(client).findInstances("in", false, listOf("/"), limit = 100)
    }

    @Test
    fun `find instances combines --refs-only with scope and filters`() {
        val client = mock<DaemonClient>()
        val scope = listOf("com.specificlanguages.json", ".structure")
        val filters = listOf(NodeFilter.Named("Json*"), NodeFilter.Role("linkDeclaration"))
        whenever(client.findInstances(CONCEPT, false, scope, filters, 100)).thenReturn(instancesResponse())

        val stdout = execute(
            client,
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

        verify(client).findInstances(CONCEPT, false, scope, filters, 100)
        assertEquals(testNode.reference + System.lineSeparator(), stdout)
    }

    @Test
    fun `refs-only output feeds back into model get-node`() {
        val findClient = mock<DaemonClient>()
        whenever(findClient.findInstances(CONCEPT, false, limit = 100)).thenReturn(instancesResponse())

        val reference = execute(findClient, "--refs-only", CONCEPT).trim()

        val getNodeClient = mock<DaemonClient>()
        val target = NodeTarget.NodeReference(reference)
        whenever(getNodeClient.getNode(target)).thenReturn(
            ModelGetNodeResponse(
                node = MpsNodeJson(model = TEST_MODEL, concept = testNode.concept, id = "2110045694544566905"),
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

    /** Runs `find instances` with [args], checks that it succeeded, and returns its stdout. */
    private fun execute(client: DaemonClient, vararg args: String): String {
        var exitCode = Int.MIN_VALUE
        val stdout = tapSystemOut {
            exitCode = CommandLine(FindInstancesCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(*args)
        }
        assertEquals(0, exitCode, stdout)
        return stdout
    }

    private companion object {
        const val CONCEPT = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
    }
}
