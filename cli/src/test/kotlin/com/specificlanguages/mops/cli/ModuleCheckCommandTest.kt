package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.check.ModuleCheckCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

/** Tests what `check module` selects. [CheckCommandTest] covers the output and exit code shared by every subcommand. */
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
        whenever(client.checkModules(modules, 20)).thenReturn(checkResponse())

        val stdout = tapSystemOut {
            val exitCode = CommandLine(ModuleCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(*modules.toTypedArray())
            assertEquals(0, exitCode)
        }

        verify(client).checkModules(modules, 20)
        assertEquals("# checked 3 roots: no findings", stdout.trim())
    }

    @Test
    fun `module check names several modules without roots in the plural`() {
        val client = mock<DaemonClient>()
        val modules = listOf("FOO", "BAR")
        whenever(client.checkModules(modules, 20)).thenReturn(checkResponse(rootsChecked = 0))

        val stderr = tapSystemErr {
            CommandLine(ModuleCheckCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(*modules.toTypedArray())
        }

        assertEquals("modules FOO, BAR have no roots; nothing was checked", stderr.trim())
    }
}
