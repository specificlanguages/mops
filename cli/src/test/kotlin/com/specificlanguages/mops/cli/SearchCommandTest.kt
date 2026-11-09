package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.find.FindInstancesCommand
import com.specificlanguages.mops.cli.find.FindNodeByIdCommand
import com.specificlanguages.mops.cli.find.FindRootByNameCommand
import com.specificlanguages.mops.cli.find.FindUsagesCommand
import com.specificlanguages.mops.cli.find.SearchCommand
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine

/**
 * Tests the behavior that every bounded `find` subcommand inherits from [SearchCommand]: the output modes, the empty
 * result line, `--limit`, and the scope clause. Each test runs once per subcommand. What a subcommand selects is tested
 * in its own test class.
 */
@ResourceLock("system-streams")
class SearchCommandTest {
    @ParameterizedTest
    @MethodSource("subcommands")
    fun `prints one tab-separated row per match`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(listOf(testNode)))

        val run = subcommand.run(client)

        assertEquals(0, run.exitCode, run.stderr)
        subcommand.call(verify(client), null, 100)
        assertEquals(subcommand.row(testNode, "JsonObject\tConceptDeclaration\t${testNode.reference}") + NL, run.stdout)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `renders an unnamed node as unnamed`(subcommand: SearchSubcommand) {
        val node = testNode.copy(name = null)
        val client = subcommand.returning(subcommand.response(listOf(node)))

        val run = subcommand.run(client)

        assertEquals(subcommand.row(node, "<unnamed>\tConceptDeclaration\t${node.reference}") + NL, run.stdout)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `keeps a node whose name has a newline on one row`(subcommand: SearchSubcommand) {
        val node = testNode.copy(name = "first\nsecond")
        val client = subcommand.returning(subcommand.response(listOf(node)))

        val run = subcommand.run(client)

        assertEquals(subcommand.row(node, "first\\nsecond\tConceptDeclaration\t${node.reference}") + NL, run.stdout)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `appends parent columns when the node has a parent`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(listOf(testChildNode)))

        val run = subcommand.run(client)

        assertEquals(
            subcommand.row(
                testChildNode,
                "content\tLinkDeclaration\t${testChildNode.reference}\t" +
                    "parent\tJsonObject\tConceptDeclaration\t${testNode.reference}",
            ) + NL,
            run.stdout,
        )
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `shows fully qualified concept names with --full-concept`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(listOf(testChildNode)))

        val run = subcommand.run(client, "--full-concept")

        assertEquals(
            subcommand.row(
                testChildNode,
                "content\tjetbrains.mps.lang.structure.structure.LinkDeclaration\t${testChildNode.reference}\t" +
                    "parent\tJsonObject\tjetbrains.mps.lang.structure.structure.ConceptDeclaration\t" +
                    testNode.reference,
            ) + NL,
            run.stdout,
        )
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `renders node references as URLs when configured globally`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(listOf(testNode)))

        val run = subcommand.run(client, format = NodeReferenceFormat.URL)

        assertEquals(
            subcommand.row(
                testNode,
                "JsonObject\tConceptDeclaration\thttp://127.0.0.1:63320/node?ref=" +
                    "r%3Afd752404-89d3-4ffe-bc3a-7fb7a27c63b6%28com.specificlanguages.json.structure%29%2F" +
                    "2110045694544566905",
            ) + NL,
            run.stdout,
        )
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `prints the response object as json when requested`(subcommand: SearchSubcommand) {
        val response = subcommand.response(listOf(testNode))
        val client = subcommand.returning(response)

        val run = subcommand.run(client, "--json")

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(response, ProtocolJson.decodeResponse(run.stdout))
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `names the subject and scope in one metadata line when nothing matches`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(emptyList()))

        val run = subcommand.run(client)

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals("# no ${subcommand.emptySubject} in editable project sources (searched 1 model)" + NL, run.stdout)
        assertEquals("", run.stderr)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `reports an empty --refs-only result on stderr keeping stdout empty`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(emptyList()))

        val run = subcommand.run(client, "--refs-only")

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals("", run.stdout)
        assertEquals("# no ${subcommand.emptySubject} in editable project sources (searched 1 model)" + NL, run.stderr)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `prints only the response for an empty --json result`(subcommand: SearchSubcommand) {
        val response = subcommand.response(emptyList())
        val client = subcommand.returning(response)

        val run = subcommand.run(client, "--json")

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(response, ProtocolJson.decodeResponse(run.stdout))
        assertEquals("", run.stderr)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `prints one node reference per line with --refs-only`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(listOf(testNode, testChildNode)))

        val run = subcommand.run(client, "--refs-only")

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(testNode.reference + NL + testChildNode.reference + NL, run.stdout)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `rejects --refs-only combined with --json`(subcommand: SearchSubcommand) {
        val client = mock<DaemonClient>()

        val run = subcommand.run(client, "--refs-only", "--json")

        assertEquals(1, run.exitCode)
        verifyNoInteractions(client)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `passes zero to the daemon as an unlimited limit`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(listOf(testNode), limit = 0), limit = 0)

        val run = subcommand.run(client, "--limit", "0")

        assertEquals(0, run.exitCode, run.stderr)
        subcommand.call(verify(client), null, 0)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `appends a metadata line naming --limit when more results exist`(subcommand: SearchSubcommand) {
        val response = subcommand.response(listOf(testNode), limit = 1, truncated = true)
        val client = subcommand.returning(response, limit = 1)

        val run = subcommand.run(client, "--limit", "1")

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(
            subcommand.row(testNode, "JsonObject\tConceptDeclaration\t${testNode.reference}") + NL +
                "# truncated: showing 1; pass --limit 0 for all" + NL,
            run.stdout,
        )
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `reports --refs-only truncation on stderr keeping stdout pipe-clean`(subcommand: SearchSubcommand) {
        val response = subcommand.response(listOf(testNode), limit = 1, truncated = true)
        val client = subcommand.returning(response, limit = 1)

        val run = subcommand.run(client, "--refs-only", "--limit", "1")

        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(testNode.reference + NL, run.stdout)
        assertEquals("truncated\t1\tmore results not shown" + NL, run.stderr)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `rejects a negative limit before dispatching to the daemon`(subcommand: SearchSubcommand) {
        val client = mock<DaemonClient>()

        val run = subcommand.run(client, "--limit", "-1")

        assertEquals(1, run.exitCode)
        verifyNoInteractions(client)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `passes the in-clause scope segments to the daemon`(subcommand: SearchSubcommand) {
        val scope = listOf("com.specificlanguages.json", ".structure")
        val client = subcommand.returning(subcommand.response(listOf(testNode)), scope = scope)

        val run = subcommand.run(client, scopeClause = listOf("in") + scope)

        assertEquals(0, run.exitCode, run.stderr)
        subcommand.call(verify(client), scope, 100)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `passes in slash to the daemon as the repository scope`(subcommand: SearchSubcommand) {
        val client = subcommand.returning(subcommand.response(listOf(testNode)), scope = listOf("/"))

        val run = subcommand.run(client, scopeClause = listOf("in", "/"))

        assertEquals(0, run.exitCode, run.stderr)
        subcommand.call(verify(client), listOf("/"), 100)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `rejects an in clause with no scope segments`(subcommand: SearchSubcommand) {
        val client = mock<DaemonClient>()

        val run = subcommand.run(client, scopeClause = listOf("in"))

        assertEquals(1, run.exitCode)
        verifyNoInteractions(client)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `rejects an unknown --all option`(subcommand: SearchSubcommand) {
        val client = mock<DaemonClient>()

        val run = subcommand.run(client, "--all")

        assertEquals(2, run.exitCode)
        verifyNoInteractions(client)
    }

    /**
     * One bounded `find` subcommand: how to create it, the query arguments it runs with, the daemon call they make, and
     * how its responses and rows look.
     */
    class SearchSubcommand(
        private val name: String,
        private val query: List<String>,
        private val create: (CommandEnvironment) -> SearchCommand,
        /** Makes the daemon call that [query] selects, with a scope and limit. */
        val call: (DaemonClient, List<String>?, Int) -> DaemonResponse,
        /** Builds a response whose matches are, or are owned by, the given nodes. */
        private val respond: (List<MpsNodeSummaryJson>, Int, Boolean) -> DaemonResponse,
        /** What the empty-result line names for a [respond] response. */
        val emptySubject: String,
        /** The columns that a row starts with, before the node's name, concept, and reference. */
        private val rowStart: (MpsNodeSummaryJson) -> String,
    ) {
        fun response(nodes: List<MpsNodeSummaryJson>, limit: Int = 100, truncated: Boolean = false): DaemonResponse =
            respond(nodes, limit, truncated)

        /** The text row for a match on [node], whose name, concept, and reference columns are [nodeColumns]. */
        fun row(node: MpsNodeSummaryJson, nodeColumns: String): String = rowStart(node) + "\t" + nodeColumns

        fun returning(response: DaemonResponse, scope: List<String>? = null, limit: Int = 100): DaemonClient {
            val client = mock<DaemonClient>()
            whenever(call(client, scope, limit)).thenReturn(response)
            return client
        }

        fun run(
            client: DaemonClient,
            vararg options: String,
            scopeClause: List<String> = emptyList(),
            format: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
        ): SearchRun {
            var exitCode = Int.MIN_VALUE
            var stdout = ""
            val stderr = tapSystemErr {
                stdout = tapSystemOut {
                    exitCode = CommandLine(create(DaemonClientCommandEnvironment(client, format)))
                        .setExecutionExceptionHandler(PrintErrorAndExit)
                        .execute(*options, *query.toTypedArray(), *scopeClause.toTypedArray())
                }
            }
            return SearchRun(exitCode, stdout, stderr)
        }

        override fun toString(): String = name
    }

    class SearchRun(val exitCode: Int, val stdout: String, val stderr: String)

    companion object {
        private val NL = System.lineSeparator()
        private const val CONCEPT = "ConceptDeclaration"
        private const val ID = "2110045694544566904"
        private const val PATTERN = "Json"
        private val TARGET = NodeTarget.NodeReference(testUsagesTarget.reference)

        @JvmStatic
        fun subcommands(): List<SearchSubcommand> = listOf(
            SearchSubcommand(
                name = "instances",
                query = listOf(CONCEPT),
                create = ::FindInstancesCommand,
                call = { client, scope, limit -> client.findInstances(CONCEPT, false, scope, limit = limit) },
                respond = { nodes, limit, truncated -> instancesResponse(nodes, limit, truncated) },
                emptySubject = "instances of ${testConceptTarget.concept}",
                rowStart = { it.type },
            ),
            SearchSubcommand(
                name = "usages",
                query = listOf(testUsagesTarget.reference),
                create = ::FindUsagesCommand,
                call = { client, scope, limit -> client.findUsages(TARGET, scope, limit) },
                respond = { owners, limit, truncated -> usagesResponse(owners, limit, truncated) },
                emptySubject = "usages of ${testUsagesTarget.name} (Target)",
                rowStart = { "usage\tconcept" },
            ),
            SearchSubcommand(
                name = "root-by-name",
                query = listOf(PATTERN),
                create = ::FindRootByNameCommand,
                call = { client, scope, limit -> client.findByName(PATTERN, scope, limit) },
                respond = { nodes, limit, truncated -> byNameResponse(nodes, limit, truncated) },
                emptySubject = "root nodes matching ${testPatternTarget.pattern}",
                rowStart = { it.type },
            ),
            SearchSubcommand(
                name = "node-by-id",
                query = listOf(ID),
                create = ::FindNodeByIdCommand,
                call = { client, scope, limit -> client.findNodeById(ID, scope, limit) },
                respond = { nodes, limit, truncated -> nodeIdResponse(nodes, limit, truncated) },
                emptySubject = "nodes with ID ${testNodeIdTarget.nodeId}",
                rowStart = { it.type },
            ),
        )
    }
}
