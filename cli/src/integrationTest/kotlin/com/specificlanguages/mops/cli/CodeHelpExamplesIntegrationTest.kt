package com.specificlanguages.mops.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

@ResourceLock("system-streams")
class CodeHelpExamplesIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `documented examples execute with fixture names and native references`() {
        val project = copyTestProject("base-language-sandbox", tempDir.resolve("project"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        val program = tempDir.resolve("example.groovy")
        val installed = InstalledCli(project, home, tempDir)
        fun cli(vararg args: String): CliResult = installed.run(*args)
        fun execute(path: String, substitutions: Map<String, String> = emptyMap()): String {
            val help = cli("code", "help", path, "--json")
            assertEquals(0, help.exitCode, help.output)
            val entries = Json.parseToJsonElement(help.stdout.trim()).jsonObject.getValue("entries").jsonArray
            val entry = entries.single { it.jsonObject.getValue("path").jsonPrimitive.content == path }.jsonObject
            val example = entry.getValue("examples").jsonArray.first().jsonPrimitive.content
            val source = substitutions.entries.fold(example) { text, (placeholder, value) -> text.replace(placeholder, value) }
            program.writeText(source)
            val result = cli("code", "run", program.pathString)
            assertEquals(0, result.exitCode, "$path example:\n$source\n${result.output}")
            return result.stdout.trim()
        }
        try {
            Json.parseToJsonElement(execute("Project.read")).jsonArray
            val build = Json.parseToJsonElement(execute("Project.make")).jsonObject
            assertTrue(build.getValue("outcome").jsonPrimitive.content in setOf("SUCCESS", "FAILED", "NOTHING_TO_GENERATE"))
            assertTrue(build.getValue("moduleCount").jsonPrimitive.content.toInt() >= 0)
            build.getValue("messages").jsonArray

            assertEquals("sample.solution", execute("Project.createSolution"))
            assertEquals("sample.language", execute("Project.createLanguage"))
            assertEquals("sample.devkit", execute("Project.createDevkit"))
            assertTrue(execute("Language.createGenerator").isNotBlank())
            assertEquals("sample.model", execute("SModule.createModel"))
            assertContains(execute("global.help"), "usagePreset")
            assertEquals("jetbrains.mps.baseLanguage.structure.ClassConcept", execute("mops.lookup.conceptByName"))
            assertEquals("jetbrains.mps.baseLanguage.structure.ClassConcept", execute("mops.lookup.requireConceptByName"))
            assertContains(execute("mops.parsing.java"), "addJavaClassesFromString")

            val parsed = Json.parseToJsonElement(execute(
                "mops.parsing.java.addJavaClassesFromString", mapOf("sample.model" to "baselanguage.sandbox"),
            )).jsonObject
            assertEquals(1, parsed.getValue("nodes").jsonArray.size)
            val classifier = parsed.getValue("nodes").jsonArray.single().jsonPrimitive.content
            val members = Json.parseToJsonElement(execute(
                "mops.parsing.java.addJavaMembersFromString", mapOf("CLASSIFIER_NODE_REFERENCE" to classifier),
            )).jsonObject
            assertEquals(1, members.getValue("nodes").jsonArray.size)
            assertEquals("Example", execute("SNode.properties", mapOf("NODE_REFERENCE" to classifier)))
            Json.parseToJsonElement(execute("SNode.children", mapOf("NODE_REFERENCE" to classifier))).jsonArray
            Json.parseToJsonElement(execute("mops.search.eachInstanceOf")).jsonArray
            Json.parseToJsonElement(execute("mops.search.eachUsageOf", mapOf("NODE_REFERENCE" to classifier))).jsonArray
        } finally {
            cli("daemon", "stop")
        }
    }
}
