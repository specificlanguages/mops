package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.create.CreateSolutionCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class CreateSolutionCommandTest {
    @Test
    fun `report renders the usage preset and the facets it produced`() {
        val client = mock<DaemonClient>()
        val report = ModuleCreationReport(
            ModuleCreationEntry(
                "s:1(example.tests)", "example.tests", ModuleKind.SOLUTION, "/p/solutions/example.tests/example.tests.msd",
                usagePreset = SolutionUsagePreset.JAVA_TESTS,
                facets = listOf(
                    FacetMementoJson("java", mapOf("compile" to "mps", "classes" to "mps", "ext" to "no")),
                    FacetMementoJson("tests"),
                ),
            ),
        )
        whenever(client.createSolution("example.tests", null, SolutionUsagePreset.JAVA_TESTS, false))
            .thenReturn(ModuleCreationResponse(report = report))

        val output = tapSystemOut {
            assertEquals(0, CommandLine(CreateSolutionCommand(client)).execute("example.tests", "--usage-preset", "java-tests"))
        }

        verify(client).createSolution("example.tests", null, SolutionUsagePreset.JAVA_TESTS, false)
        assertEquals(
            """
            created solution example.tests [s:1(example.tests)] at /p/solutions/example.tests/example.tests.msd
              usage preset: java-tests
              facet java: {compile=mps, classes=mps, ext=no}
              facet tests: {}

            """.trimIndent(),
            output,
        )
    }

    @Test
    fun `dry run plan prints the default preset and no facets`() {
        val client = mock<DaemonClient>()
        val plan = ModuleCreationPlan(
            ModuleCreationPlanEntry(
                "example.solution", ModuleKind.SOLUTION, "/p/solutions/example.solution/example.solution.msd",
                usagePreset = SolutionUsagePreset.NOT_GENERATED,
            ),
        )
        whenever(client.createSolution("example.solution", null, SolutionUsagePreset.NOT_GENERATED, true))
            .thenReturn(ModuleCreationResponse(plan = plan))

        val output = tapSystemOut {
            assertEquals(0, CommandLine(CreateSolutionCommand(client)).execute("example.solution", "--dry-run"))
        }

        assertEquals(
            """
            would create solution example.solution at /p/solutions/example.solution/example.solution.msd
              usage preset: not-generated

            """.trimIndent(),
            output,
        )
    }

    @Test
    fun `unknown usage preset is a usage error that lists the accepted values`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = CommandLine(CreateSolutionCommand(client)).execute("example.solution", "--usage-preset", "bogus")
        }

        assertEquals(2, exitCode)
        assertContains(stderr, "Invalid value for option '--usage-preset': expected one of not-generated, text, java, java-tests, java-mps-plugin")
        verifyNoInteractions(client)
    }
}
