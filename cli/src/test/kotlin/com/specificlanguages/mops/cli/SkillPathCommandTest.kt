package com.specificlanguages.mops.cli

import org.junit.jupiter.api.Tag
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.skill.BundledSkill
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

@ResourceLock("system-streams")
@Tag("smoke")
class SkillPathCommandTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `default and named skills print only an existing absolute skill path outside any project`() {
        for (args in listOf(emptyArray(), arrayOf("mops"), arrayOf("mops-daemon"), arrayOf("daemon"))) {
            var exitCode = -1
            val output = tapSystemOut {
                exitCode = newCommandLine(tempDir).execute("skill", "path", *args)
            }
            assertEquals(0, exitCode)
            val expected = BundledSkill.path(args.firstOrNull() ?: "mops")
            assertEquals("$expected${System.lineSeparator()}", output)
            assertTrue(Path.of(output.trim()).isAbsolute)
            assertContains(Path.of(output.trim()).readText(), "name: ${expected.parent.fileName}")
        }
    }

    @Test
    fun `unknown skill and extra arguments fail without printing a path`() {
        for (args in listOf(arrayOf("missing"), arrayOf("mops", "extra"))) {
            var exitCode = 0
            val stderr = tapSystemErr {
                val stdout = tapSystemOut {
                    exitCode = newCommandLine(tempDir).execute("skill", "path", *args)
                }
                assertEquals("", stdout)
            }
            assertNotEquals(0, exitCode)
            assertTrue(stderr.isNotBlank())
        }
    }

    @Test
    fun `skill group and path support help`() {
        for (args in listOf(arrayOf("skill"), arrayOf("skill", "--help"), arrayOf("skill", "path", "--help"), arrayOf("help", "skill", "path"))) {
            val stdout = tapSystemOut { assertEquals(0, newCommandLine(tempDir).execute(*args)) }
            assertContains(stdout, "Usage:")
            assertContains(stdout, "path")
        }
    }

    @Test
    fun `resolution follows relocated distribution and source class locations rather than cwd`() {
        val install = tempDir.resolve("relocated installation").createDirectories()
        val skill = install.resolve("skills/mops/SKILL.md")
        skill.parent.createDirectories()
        skill.writeText("installed skill")
        val jar = install.resolve("lib/mops-cli.jar")
        jar.parent.createDirectories()
        jar.writeText("")
        assertEquals(skill, BundledSkill.path(codeLocation = jar))
        val daemonSkill = install.resolve("skills/mops-daemon/SKILL.md")
        daemonSkill.parent.createDirectories()
        daemonSkill.writeText("daemon skill")
        assertEquals(daemonSkill, BundledSkill.path("daemon", jar))
        assertEquals(daemonSkill, BundledSkill.path("mops-daemon", jar))

        val source = tempDir.resolve("source checkout").createDirectories()
        val sourceSkill = source.resolve("skills/mops/SKILL.md")
        sourceSkill.parent.createDirectories()
        sourceSkill.writeText("source skill")
        val classes = source.resolve("cli/build/classes/kotlin/main").createDirectories()
        assertEquals(sourceSkill, BundledSkill.path(codeLocation = classes))
    }

    @Test
    fun `missing bundled skill reports a useful error`() {
        val failure = assertFailsWith<IllegalStateException> { BundledSkill.path(codeLocation = tempDir) }
        assertContains(failure.message.orEmpty(), "Could not locate the bundled mops skill")
    }
}
