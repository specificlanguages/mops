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
        assertContains(output, "edit")
        assertContains(output, "render-node")
    }

    @Test
    fun `find help lists find subcommands`() {
        val output = runHelp("find", "--help")

        assertContains(output, "instances")
        assertContains(output, "usages")
        assertContains(output, "root-by-name")
        assertContains(output, "node-by-id")
    }

    @Test
    fun `help verb prints root usage`() {
        val output = runHelp("help")

        assertContains(output, "Usage:")
        assertContains(output, "model")
    }

    @Test
    fun `help verb prints group usage`() {
        val output = runHelp("help", "model")

        assertContains(output, "Usage:")
        assertContains(output, "edit")
    }

    @Test
    fun `help verb prints leaf usage`() {
        val output = runHelp("help", "model", "edit")

        assertContains(output, "Usage:")
        assertContains(output, "--file")
        assertContains(output, "--constraints")
        assertContains(output, "Operation reference: mops explain edit")
    }

    @Test
    fun `group help verb prints leaf usage`() {
        val output = runHelp("model", "help", "edit")

        assertContains(output, "Usage:")
        assertContains(output, "--file")
    }

    @Test
    fun `daemon help verb prints ping usage`() {
        val output = runHelp("daemon", "help", "ping")

        assertContains(output, "Usage:")
        assertContains(output, "ping")
    }

    @Test
    fun `every leaf command supports --help`() {
        val leafCommands = listOf(
            arrayOf("list"),
            arrayOf("find", "instances"),
            arrayOf("find", "usages"),
            arrayOf("find", "root-by-name"),
            arrayOf("find", "node-by-id"),
            arrayOf("model", "get-node"),
            arrayOf("model", "render-node"),
            arrayOf("model", "edit"),
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
