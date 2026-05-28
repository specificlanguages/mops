package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class CommandHelpTest {
    @Test
    fun `root help lists top-level commands`() {
        val output = runHelp("--help")

        assertContains(output, "daemon")
        assertContains(output, "list, ls")
        assertContains(output, "model")
    }

    @Test
    fun `daemon help lists daemon subcommands`() {
        val output = runHelp("daemon", "--help")

        assertContains(output, "ping")
        assertContains(output, "status")
        assertContains(output, "stop")
    }

    @Test
    fun `model help lists model subcommands`() {
        val output = runHelp("model", "--help")

        assertContains(output, "get-node")
        assertContains(output, "resave")
    }

    private fun runHelp(vararg args: String): String {
        var exitCode = Int.MIN_VALUE
        val stdout = tapSystemOut {
            exitCode = newCommandLine().execute(*args)
        }

        assertEquals(0, exitCode)
        return stdout
    }
}
