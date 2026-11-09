package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.check.CheckCommand
import com.specificlanguages.mops.cli.check.ModelCheckCommand
import com.specificlanguages.mops.cli.check.ModuleCheckCommand
import com.specificlanguages.mops.cli.check.ProjectCheckCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import com.specificlanguages.mops.protocol.ModelCheckSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

/**
 * Tests the behavior that every `check` subcommand inherits from [CheckCommand]: rendering in both formats, the
 * `--limit` cap, and the exit code. Each test runs once per subcommand. What a subcommand selects is tested in its own
 * test class.
 */
@ResourceLock("system-streams")
class CheckCommandTest {
    @ParameterizedTest
    @MethodSource("subcommands")
    fun `prints a readable severity-sorted list with a summary metadata line by default`(subcommand: CheckSubcommand) {
        val client = subcommand.returning(checkResponse(listOf(ERROR_FINDING, WARNING_FINDING)))

        val run = subcommand.run(client)

        assertEquals(1, run.exitCode, "an error finding fails the check")
        subcommand.call(verify(client), 20)
        assertEquals(listOf(ERROR_ROW, "warning\tmodel-level note", "# 1 error, 1 warning, 0 infos"), run.lines)
        // The human list is the user-facing payload only: no daemon wrapper fields.
        assertFalse(run.stdout.contains("\"limit\""), "human output must not leak wrapper fields: ${run.stdout}")
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `a result with findings but no errors reads zero errors in the summary and passes`(subcommand: CheckSubcommand) {
        val client = subcommand.returning(checkResponse(listOf(WARNING_FINDING), warnings = 3, infos = 12))

        val run = subcommand.run(client)

        assertEquals(0, run.exitCode, "warnings and infos alone do not fail the check")
        assertEquals(listOf("warning\tmodel-level note", "# 0 errors, 3 warnings, 12 infos"), run.lines)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `an empty result summarises as no findings and passes`(subcommand: CheckSubcommand) {
        val client = subcommand.returning(checkResponse())

        val run = subcommand.run(client)

        assertEquals(0, run.exitCode)
        assertEquals(listOf("# no findings"), run.lines)
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `exits 1 when the counts include an error, in both formats`(subcommand: CheckSubcommand) {
        val client = subcommand.returning(checkResponse(listOf(ERROR_FINDING)))

        for (options in listOf(emptyArray(), arrayOf("--format", "jsonl"))) {
            val run = subcommand.run(client, *options)
            assertEquals(1, run.exitCode, "${options.toList()}: ${run.stdout}")
        }
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `jsonl output is one finding per line followed by a summary record`(subcommand: CheckSubcommand) {
        val response = checkResponse(listOf(ERROR_FINDING, WARNING_FINDING))
        val client = subcommand.returning(response)

        val run = subcommand.run(client, "--format", "JsOnL")

        assertEquals(1, run.exitCode)
        assertEquals(3, run.lines.size, "each finding prints on its own line, then the summary: ${run.stdout}")
        assertEquals(ERROR_FINDING, ProtocolJson.finding.decode(run.lines[0]))
        assertEquals(WARNING_FINDING, ProtocolJson.finding.decode(run.lines[1]))
        assertEquals(
            ModelCheckSummaryJson(shown = 2, summary = response.summary),
            ProtocolJson.checkSummary.decode(run.lines[2]),
        )
        // The payload is the findings and the summary, not the daemon response wrapper.
        assertFalse(run.stdout.contains("\"findings\""), "jsonl must print bare findings, not wrapped responses: ${run.stdout}")
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `a truncated jsonl summary names the option that lifts the limit`(subcommand: CheckSubcommand) {
        val response = checkResponse(listOf(WARNING_FINDING), warnings = 2, infos = 5, limit = 1, truncated = true)
        val client = subcommand.returning(response, limit = 1)

        val run = subcommand.run(client, "--format", "jsonl", "--limit", "1")

        assertEquals(0, run.exitCode)
        assertEquals(2, run.lines.size, run.stdout)
        assertEquals(
            ModelCheckSummaryJson(shown = 1, summary = response.summary, hint = "pass --limit 0 for all"),
            ProtocolJson.checkSummary.decode(run.lines[1]),
        )
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `a truncated human summary is followed by a truncation metadata line`(subcommand: CheckSubcommand) {
        val response = checkResponse(
            listOf(ERROR_FINDING),
            errors = 3,
            warnings = 2,
            infos = 5,
            limit = 1,
            truncated = true,
        )
        val client = subcommand.returning(response, limit = 1)

        val run = subcommand.run(client, "--limit", "1")

        // Totals and the exit decision are over the full set, not the shown slice.
        assertEquals(1, run.exitCode)
        subcommand.call(verify(client), 1)
        assertEquals(
            listOf(
                ERROR_ROW,
                "# 3 errors, 2 warnings, 5 infos",
                "# truncated: showing 1 of 10 findings; pass --limit 0 for all",
            ),
            run.lines,
        )
    }

    @ParameterizedTest
    @MethodSource("subcommands")
    fun `rejects an unknown format`(subcommand: CheckSubcommand) {
        val client = subcommand.returning(checkResponse())

        val run = subcommand.run(client, "--format", "xml")

        assertEquals(2, run.exitCode)
        assertContains(run.stderr, "unknown --format value 'xml'")
    }

    /** One `check` subcommand: how to create it, the arguments that select what it checks, and the call it makes. */
    class CheckSubcommand(
        private val name: String,
        private val selection: List<String>,
        private val create: (DaemonClient) -> CheckCommand,
        val call: (DaemonClient, Int) -> ModelCheckResponse,
    ) {
        fun returning(response: ModelCheckResponse, limit: Int = 20): DaemonClient {
            val client = mock<DaemonClient>()
            whenever(call(client, limit)).thenReturn(response)
            return client
        }

        fun run(client: DaemonClient, vararg options: String): CheckRun {
            var exitCode = Int.MIN_VALUE
            var stdout = ""
            val stderr = tapSystemErr {
                stdout = tapSystemOut {
                    exitCode = CommandLine(create(client))
                        .setExecutionExceptionHandler(PrintErrorAndExit)
                        .execute(*options, *selection.toTypedArray())
                }
            }
            return CheckRun(exitCode, stdout, stderr)
        }

        override fun toString(): String = name
    }

    class CheckRun(val exitCode: Int, val stdout: String, val stderr: String) {
        val lines: List<String> get() = stdout.trim().lines()
    }

    companion object {
        private const val MODEL = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"

        @JvmStatic
        fun subcommands(): List<CheckSubcommand> = listOf(
            CheckSubcommand("model", listOf(MODEL), ::ModelCheckCommand) { client, limit ->
                client.checkModel(MODEL, limit)
            },
            CheckSubcommand("module", listOf("FOO"), ::ModuleCheckCommand) { client, limit ->
                client.checkModules(listOf("FOO"), limit)
            },
            CheckSubcommand("project", emptyList(), ::ProjectCheckCommand) { client, limit ->
                client.checkProject(limit)
            },
        )

        private val ERROR_FINDING = ModelCheckFindingJson(
            severity = FindingSeverity.ERROR,
            message = "Unresolved reference",
            node = MpsNodeSummaryJson(
                type = "node",
                name = "b",
                concept = "jetbrains.mps.baseLanguage.structure.VariableReference",
                reference = "$MODEL/ref",
            ),
        )

        private const val ERROR_ROW =
            "error\tUnresolved reference\tb\tjetbrains.mps.baseLanguage.structure.VariableReference\t$MODEL/ref"

        private val WARNING_FINDING = ModelCheckFindingJson(
            severity = FindingSeverity.WARNING,
            message = "model-level note",
        )
    }
}
