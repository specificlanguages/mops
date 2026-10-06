package com.specificlanguages.mops.cli

import groovy.lang.Binding
import groovy.lang.GroovyShell
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

@Tag("smoke")
class ExampleGeneratorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `one group slot includes every member and preserves other groups`() {
        val specs = """
            [
                first: [group: 'commands', title: 'First task', kind: 'cli', code: 'mops list'],
                second: [group: 'commands', title: 'Second task', kind: 'cli', code: 'mops list /'],
                help: [group: 'guidance', kind: 'cli', code: 'mops --help'],
            ]
        """.trimIndent()
        val output = generate("# Tasks\n\n{{table:commands|Command}}\n\nRead `{{help}}`.\n", specs)
        assertEquals("""
            # Tasks

            | Task        | Command       |
            | ----------- | ------------- |
            | First task  | `mops list`   |
            | Second task | `mops list /` |

            Read `mops --help`.

        """.trimIndent(), output.resolve("pages/sample.md").readText())
        val catalog = Json.parseToJsonElement(output.resolve("specs/examples.json").readText()).jsonArray
        assertEquals(listOf("commands", "commands", "guidance"), catalog.map { it.jsonObject.getValue("group").jsonPrimitive.content })
    }

    @Test
    fun `empty table groups fail generation`() {
        val error = assertFailsWith<AssertionError> { generate("{{table:missing|Command}}", "[:]") }
        assertContains(error.message.orEmpty(), "unknown or empty table group 'missing'")
    }

    @Test
    fun `unused examples fail generation`() {
        val error = assertFailsWith<AssertionError> {
            generate("# Tasks\n", "[list: [group: 'commands', title: 'List', kind: 'cli', code: 'mops list']]")
        }
        assertContains(error.message.orEmpty(), "undocumented examples [list]")
    }

    private fun generate(page: String, specs: String): Path {
        val source = tempDir.resolve("source").createDirectories()
        source.resolve("pages").createDirectories().resolve("sample.md").writeText(page)
        source.resolve("specs").createDirectories().resolve("sample.groovy").writeText(specs)
        val output = tempDir.resolve("output")
        val script = checkNotNull(javaClass.getResourceAsStream("/generate.groovy")).bufferedReader().use { it.readText() }
        GroovyShell(Binding(mapOf("sourceDir" to source.toFile(), "outputDir" to output.toFile()))).evaluate(script)
        return output
    }
}
