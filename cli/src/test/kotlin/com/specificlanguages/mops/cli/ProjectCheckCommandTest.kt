package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.check.ProjectCheckCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class ProjectCheckCommandTest {
    @Test
    fun `project check checks all project modules with the default limit`() {
        val client = mock<DaemonClient>()
        whenever(client.checkProject(20)).thenReturn(NO_FINDINGS)

        val stdout = tapSystemOut {
            val exitCode = CommandLine(ProjectCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute()
            assertEquals(0, exitCode)
        }

        verify(client).checkProject(20)
        assertEquals("no findings", stdout.trim())
    }

    private companion object {
        val NO_FINDINGS = ModelCheckResponse(
            limit = 20,
            truncated = false,
            totals = ModelCheckFindingCounts(errors = 0, warnings = 0, infos = 0),
            findings = emptyList(),
        )
    }
}
