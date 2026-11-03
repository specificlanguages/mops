package com.specificlanguages.mops.cli

import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GlobalOptionsTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `global options update the root command at every position in a nested command`() {
        val arguments = listOf("find", "instances", "example.language.structure.Example")
        val options = listOf(
            "--mps-home", "MPS home",
            "--java-home", "Java home",
            "--daemon-home", "Daemon home",
            "--project-root", "MPS project",
            "--refs-as-urls",
        )

        for (position in 0..arguments.size) {
            val command = newCommandLine()
            command.parseArgs(*(arguments.take(position) + options + arguments.drop(position)).toTypedArray())
            val root = command.commandSpec.userObject() as MopsCommand

            assertEquals(
                listOf("MPS home", "Java home", "Daemon home", "MPS project"),
                listOf(root.mpsHome, root.javaHome, root.daemonHome, root.projectRoot),
                "Options inserted at position $position",
            )
            assertEquals(NodeReferenceFormat.URL, root.nodeReferenceFormat)
            assertEquals("instances", command.parseResult.subcommand().subcommand().commandSpec().name())
        }
    }

    @Test
    fun `path macros collect from every position and resolve against the working directory`() {
        val workingDirectory = temporary.resolve("work").createDirectories()
        val first = workingDirectory.resolve("first").createDirectories()
        val second = temporary.resolve("second").createDirectories()
        val command = newCommandLine(workingDirectory)
        command.parseArgs(
            "--macro", "first=first", "--macro", "work=.", "find", "--macro=second=${second.pathString}", "instances",
            "x.Y", "--macro", "last=first",
        )
        val root = command.commandSpec.userObject() as MopsCommand

        assertEquals(
            mapOf("first" to first, "work" to workingDirectory, "second" to second, "last" to first),
            root.resolveMacros(),
        )
    }

    @Test
    fun `path macros must name a directory and not an MPS built-in macro`() {
        fun failure(macro: String): String {
            val command = newCommandLine(temporary)
            command.parseArgs("--macro", macro, "daemon", "ping")
            val root = command.commandSpec.userObject() as MopsCommand
            return assertFailsWith<IllegalArgumentException> { root.resolveMacros() }.message!!
        }

        assertEquals("--macro repo.root=missing: ${temporary.resolve("missing")} is not a directory", failure("repo.root=missing"))
        assertEquals(
            "--macro mps_home=.: mps_home is a path macro MPS defines itself and cannot be set",
            failure("mps_home=."),
        )
        fun parseFailure(vararg args: String) =
            assertFailsWith<CommandLine.ParameterException> { newCommandLine(temporary).parseArgs(*args) }.message
        assertEquals("--macro expects NAME=PATH, got: repo.root", parseFailure("--macro", "repo.root"))
        assertEquals("--macro expects NAME=PATH, got: repo.root=", parseFailure("--macro", "repo.root="))
        assertEquals(
            "--macro repo.root is given twice: a and b",
            parseFailure("--macro", "repo.root=a", "--macro", "repo.root=b"),
        )
    }
}
