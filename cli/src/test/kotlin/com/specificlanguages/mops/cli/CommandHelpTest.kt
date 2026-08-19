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
        assertContains(output, "get")
        assertContains(output, "render")
        assertContains(output, "edit")
        assertContains(output, "check")
        assertContains(output, "--project-root")
    }

    @Test
    fun `edit help lists model subcommand`() {
        val output = runHelp("edit", "--help")

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
    fun `verb help lists object subcommands`() {
        assertContains(runHelp("get", "--help"), "node")
        assertContains(runHelp("render", "--help"), "node")
        assertContains(runHelp("edit", "--help"), "model")
        assertContains(runHelp("check", "--help"), "model")
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
        assertContains(output, "get")
    }

    @Test
    fun `help verb prints group usage`() {
        val output = runHelp("help", "edit")

        assertContains(output, "Usage:")
        assertContains(output, "model")
    }

    @Test
    fun `help verb prints leaf usage`() {
        val output = runHelp("help", "edit", "model")

        assertContains(output, "Usage:")
        assertContains(output, "--file")
        assertContains(output, "--constraints")
        assertContains(output, "Operation reference: mops explain edit")
    }

    @Test
    fun `group help verb prints leaf usage`() {
        val output = runHelp("edit", "help", "model")

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
            arrayOf("get", "node"),
            arrayOf("render", "node"),
            arrayOf("edit", "model"),
            arrayOf("check", "model"),
            arrayOf("make", "module"),
            arrayOf("make", "project"),
            arrayOf("diagnose", "module"),
            arrayOf("diagnose", "project"),
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
