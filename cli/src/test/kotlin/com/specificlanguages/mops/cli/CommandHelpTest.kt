package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ResourceLock("system-streams")
class CommandHelpTest {
    private val groups = listOf("find", "get", "render", "edit", "check", "make", "create", "diagnose", "daemon", "code", "skill")

    @Test
    fun `refs as URLs is a global option`() {
        val commandLine = newCommandLine()

        commandLine.parseArgs("--refs-as-urls", "find", "instances", "example.language.structure.Example")

        assertEquals(
            NodeReferenceFormat.URL,
            (commandLine.commandSpec.userObject() as MopsCommand).nodeReferenceFormat,
        )
    }

    @Test
    fun `root help lists top-level commands`() {
        val output = runHelp("--help")

        assertContains(output, "daemon")
        assertContains(output, "wrapper")
        assertFalse(output.contains("create-launcher"))
        assertContains(output, "list, ls")
        assertContains(output, "get")
        assertContains(output, "render")
        assertContains(output, "edit")
        assertContains(output, "check")
        assertContains(output, "--project-root")
        assertContains(output, "--refs-as-urls")
    }

    @Test
    fun `bare mops prints the same usage as --help`() {
        assertEquals(runHelp("--help"), runHelp())
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
        val checkHelp = runHelp("check", "--help")
        assertContains(checkHelp, "Run MPS checks.")
        assertContains(checkHelp, "model")
        assertContains(checkHelp, "module")
        assertContains(checkHelp, "project")
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
    fun `group help lists commands before options`() {
        for (group in groups) {
            val output = runHelp(group, "--help")

            val commands = output.indexOf("Commands:")
            val options = output.indexOf("Options:")
            assertTrue(commands >= 0, "$group help should have a Commands section")
            assertTrue(options >= 0, "$group help should have an Options section")
            assertTrue(commands < options, "$group help should list commands before options")
        }
    }

    @Test
    fun `every commands list entry fits on one line`() {
        for (command in listOf(emptyArray<String>()) + groups.map { arrayOf(it) }) {
            val output = runHelp(*command, "--help")
            val name = command.joinToString(" ").ifEmpty { "mops" }

            val entries = output.lines().dropWhile { it != "Commands:" }.drop(1).takeWhile { it.startsWith("  ") }
            assertTrue(entries.isNotEmpty(), "$name help should list commands")
            for (entry in entries) {
                assertTrue(entry.matches(Regex("  \\S.*")), "$name help wraps a command entry: '$entry'")
            }
        }
    }

    @Test
    fun `global options form the last section of every command's help`() {
        for (command in listOf(arrayOf(), arrayOf("find"), arrayOf("find", "instances"), arrayOf("get", "node"), arrayOf("explain"))) {
            val output = runHelp(*command, "--help")
            val name = command.joinToString(" ").ifEmpty { "mops" }

            val options = output.indexOf("Options:")
            val globalOptions = output.indexOf("Global options:")
            assertTrue(options in 0 until globalOptions, "$name help should list options before global options")
            assertEquals(1, Regex("--mps-home").findAll(output).count(), "$name help should list --mps-home once")
            assertTrue(output.indexOf("--mps-home") > globalOptions, "$name help should list --mps-home under global options")
            for (option in listOf("--mps-home", "--project-root", "--daemon-home", "--java-home", "--refs-as-urls")) {
                assertContains(output.substring(globalOptions), option)
            }
            assertFalse(output.substring(globalOptions).contains("--help"), "$name help should keep --help out of global options")
        }
    }

    @Test
    fun `find help head shows every find subcommand`() {
        val head = runHelp("find", "--help").lines().take(12).joinToString("\n")

        for (subcommand in listOf("instances", "usages", "root-by-name", "node-by-id")) {
            assertContains(head, "  $subcommand ")
        }
    }

    @Test
    fun `node commands show one synopsis per positional form`() {
        for (verb in listOf("get", "render")) {
            val output = runHelp(verb, "node", "--help")

            assertContains(output, "Usage: mops $verb node [OPTIONS] NODE_REFERENCE\n")
            assertContains(output, "   or: mops $verb node [OPTIONS] MODEL_TARGET NODE_ID\n")
            val parameters = output.substringAfter("Parameters:").substringBefore("Options:")
            assertContains(parameters, "NODE_REFERENCE | MODEL_TARGET")
            assertContains(parameters, "[NODE_ID]")
            assertFalse(parameters.contains("[NODE_REFERENCE"), "$verb node repeats the positional label:$parameters")
        }
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
    fun `find instances help names every accepted concept spelling`() {
        // Picocli wraps the description, so compare against a single-spaced copy.
        val output = runHelp("find", "instances", "--help").replace(Regex("\\s+"), " ")

        assertContains(output, "<language>.structure.<ConceptName>")
        assertContains(output, "with .structure. dropped")
        assertContains(output, "bare short name")
    }

    @Test
    fun `code run has no run-wide constraints option`() {
        assertFalse(runHelp("code", "run", "--help").contains("--constraints"))
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
            arrayOf("wrapper"),
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

    @Test
    fun `wrapper help describes the generated wrapper`() {
        val output = runHelp("wrapper", "--help")

        assertContains(output, "mopsw")
        assertContains(output, "--output")
        assertContains(output, "--project-root")
        assertContains(output, "every discovered MPS project")
        assertContains(output, "warnings when their paths are not usable yet")
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
