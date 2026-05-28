package com.specificlanguages.mops.cli

import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
        val stdout = ByteArrayOutputStream()
        val exitCode = newCommandLine()
            .also { it.out = PrintWriter(stdout, true) }
            .execute(*args)

        assertEquals(0, exitCode)
        return stdout.toString()
    }
}
