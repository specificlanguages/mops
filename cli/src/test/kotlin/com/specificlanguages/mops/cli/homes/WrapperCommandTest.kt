package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.MopsCommand
import com.specificlanguages.mops.cli.newCommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

class WrapperCommandTest {
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
}
