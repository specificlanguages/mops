package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.MopsCommand
import com.specificlanguages.mops.cli.newCommandLine
import com.specificlanguages.mops.protocol.MopsBuild
import org.junit.jupiter.api.io.TempDir
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WrapperCommandTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `project root can precede or follow wrapper`() {
        listOf(
            arrayOf("--project-root", "MPS project", "wrapper"),
            arrayOf("wrapper", "--project-root", "MPS project"),
        ).forEach { args ->
            val command = newCommandLine()
            command.parseArgs(*args)
            assertEquals("MPS project", (command.commandSpec.userObject() as MopsCommand).projectRoot)
        }
    }

    @Test
    fun `output starts with the mops version even when discovery fails`() {
        val out = StringWriter()
        val command = newCommandLine(workingDirectory = temporary)
        command.out = PrintWriter(out)
        command.err = PrintWriter(StringWriter())

        assertNotEquals(0, command.execute("wrapper"))

        assertEquals("mops version: ${MopsBuild.fullVersion}", out.toString().lineSequence().first())
    }
}
