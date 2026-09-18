package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.specificlanguages.mops.cli.check.ModuleCheckCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelCheckFindingCounts
import com.specificlanguages.mops.protocol.ModelCheckResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class ModuleCheckCommandTest {
    @Test
    fun `module check requires at least one Project Module`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("check", "module")
        }

        assertEquals(2, exitCode)
        assertContains(stderr, "Missing required parameter")
        assertContains(stderr, "MODULE")
    }

    @Test
    fun `module check checks every named project module with the default limit`() {
        val client = mock<DaemonClient>()
        val modules = listOf("FOO", "BAR", "BAZ")
        whenever(client.checkModules(modules, 20)).thenReturn(NO_FINDINGS)

        val stdout = tapSystemOut {
            val exitCode = CommandLine(ModuleCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(*modules.toTypedArray())
            assertEquals(0, exitCode)
        }

        verify(client).checkModules(modules, 20)
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
