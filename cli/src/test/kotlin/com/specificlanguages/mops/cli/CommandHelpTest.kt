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
        assertContains(output, "--project-root")
    }

    @Test
    fun `model help lists edit subcommand`() {
        val output = runHelp("model", "--help")

        assertContains(output, "edit")
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

    @Test
    fun `find help lists find subcommands`() {
        val output = runHelp("find", "--help")

        assertContains(output, "instances")
        assertContains(output, "usages")
    }

    @Test
    fun `every leaf command supports --help`() {
        val leafCommands = listOf(
            arrayOf("list"),
            arrayOf("find", "instances"),
            arrayOf("find", "usages"),
            arrayOf("model", "get-node"),
            arrayOf("model", "edit"),
            arrayOf("model", "resave"),
            arrayOf("daemon", "ping"),
            arrayOf("daemon", "status"),
            arrayOf("daemon", "stop"),
        )

        for (command in leafCommands) {
            val output = runHelp(*command, "--help")

            assertContains(output, "Usage:", message = "help for '${command.joinToString(" ")}' should print usage")
            assertContains(output, command.last())
        }
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
