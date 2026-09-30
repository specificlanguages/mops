package com.specificlanguages.mops.cli

import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalOptionsTest {
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
}
