package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.MopsCommand
import com.specificlanguages.mops.cli.newCommandLine
import com.specificlanguages.mops.protocol.MopsBuild
import org.junit.jupiter.api.io.TempDir
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    @Test
    fun `given MPS and Java homes write the wrapper of the MPS project without Gradle`() {
        val temporary = temporary.toRealPath()
        val project = temporary.resolve("MPS project").also { it.resolve(".mps").createDirectories() }
        val mpsHome = temporary.resolve("mps").createDirectories()
        val javaHome = temporary.resolve("jbr").also { home ->
            home.resolve("bin").createDirectories()
            home.resolve("bin/java").createFile().toFile().setExecutable(true)
        }
        val out = StringWriter()
        val err = StringWriter()
        val command = newCommandLine(workingDirectory = project.resolve(".mps")) { Path.of("/mops bin/mops") }
        command.out = PrintWriter(out)
        command.err = PrintWriter(err)

        val exitCode = command.execute("--mps-home", mpsHome.toString(), "--java-home", "../../jbr", "wrapper")

        assertEquals(0, exitCode, "$out$err")
        assertEquals("", err.toString())
        val wrapper = project.resolve("build/mopsw")
        assertEquals(
            "#!/bin/sh\nexec '/mops bin/mops' --mps-home='$mpsHome' --java-home='$javaHome' --project-root='$project' \"\$@\"\n",
            wrapper.readText(),
        )
        assertTrue(wrapper.isExecutable())
        assertEquals(
            listOf(
                "mops version: ${MopsBuild.fullVersion}",
                "Source: --mps-home and --java-home",
                "MPS home: ../../mps",
                "Java home: ../../jbr",
                "Project root: ..",
                "Wrapper: ../build/mopsw",
            ),
            out.toString().lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `given homes that are not usable yet still write the wrapper with warnings`() {
        val temporary = temporary.toRealPath()
        val project = temporary.resolve("project").also { it.resolve(".mps").createDirectories() }
        val err = StringWriter()
        val command = newCommandLine(workingDirectory = temporary) { Path.of("/mops") }
        command.out = PrintWriter(StringWriter())
        command.err = PrintWriter(err)
        val output = temporary.resolve("out/run-mops")

        val exitCode = command.execute(
            "--mps-home", "missing-mps", "--java-home", "missing-jbr", "--project-root", "project",
            "wrapper", "--output", "out/run-mops",
        )

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            listOf(
                "Warning: MPS home is missing: missing-mps",
                "Warning: Java home is missing or has no usable bin/java: missing-jbr",
            ),
            err.toString().lines().filter { it.isNotEmpty() },
        )
        assertEquals(
            "#!/bin/sh\nexec '/mops' --mps-home='$temporary/missing-mps' --java-home='$temporary/missing-jbr' " +
                "--project-root='$project' \"\$@\"\n",
            output.readText(),
        )
    }

    @Test
    fun `an MPS home alone uses the Java home bundled with it, as other commands do`() {
        val temporary = temporary.toRealPath()
        val project = temporary.resolve("project").also { it.resolve(".mps").createDirectories() }
        val mpsHome = temporary.resolve("mps")
        mpsHome.resolve("jbr/Contents/Home/bin").createDirectories()
        val err = StringWriter()
        val command = newCommandLine(workingDirectory = project) { Path.of("/mops") }
        command.out = PrintWriter(StringWriter())
        command.err = PrintWriter(err)

        assertEquals(0, command.execute("--mps-home", mpsHome.toString(), "wrapper"), err.toString())

        assertContains(project.resolve("build/mopsw").readText(), "--java-home='$mpsHome/jbr")
    }
}
