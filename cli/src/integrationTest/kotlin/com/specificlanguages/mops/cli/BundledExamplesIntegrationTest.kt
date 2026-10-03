package com.specificlanguages.mops.cli

import org.junit.jupiter.api.Tag
import com.specificlanguages.mops.cli.examples.ExampleTopics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.test.*

@ResourceLock("system-streams")
@Tag("smoke")
class BundledExamplesIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `bundled recipes execute through one daemon and persist their edits`() {
        val project = copyTestProject("base-language-sandbox", tempDir.resolve("project"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        val program = tempDir.resolve("example.groovy")
        fun cli(vararg args: String): CliResult {
            val install = Path.of(System.getProperty("test.cliInstall"))
            val java = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
            val output = tempDir.resolve("cli-output.txt")
            val process = ProcessBuilder(
                java.pathString, "-cp", install.resolve("lib/*").pathString,
                "com.specificlanguages.mops.cli.MainKt", "--project-root", project.pathString,
                "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args,
            ).redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                assertTrue(process.waitFor(3, TimeUnit.MINUTES), "CLI timed out: ${output.readText()}")
                return CliResult(process.exitValue(), output.readText(), "")
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
        fun run(source: String, substitutions: Map<String, String> = emptyMap()): String {
            val actual = substitutions.entries.fold(source) { text, (key, value) -> text.replace(key, value) }
            program.writeText(actual)
            val result = cli("code", "run", program.pathString)
            assertEquals(0, result.exitCode, "Recipe:\n$actual\n${result.output}")
            return result.stdout.trim()
        }
        fun blocks(topic: String): List<String> =
            Regex("""(?m)^```groovy\r?\n([\s\S]*?)^```\r?$""").findAll(ExampleTopics.page(topic))
                .map { it.groupValues[1] }.toList()

        fun daemonPid(): String = Files.walk(home).use { paths ->
            val record = paths.filter { it.fileName.toString() == "daemon.json" }.findFirst().orElseThrow()
            Json.parseToJsonElement(record.readText()).jsonObject.getValue("pid").jsonPrimitive.content
        }

        try {
            val fixture = Json.parseToJsonElement(run("""
                project.command {
                    def model = mops.lookup.requireModel('baselanguage.sandbox')
                    def parsed = mops.parsing.java.addJavaClassesFromString(model,
                        'public class Example { private Example peer; public int answer() { return 1; } }')
                    def root = parsed.nodes[0]
                    def method = root.children['member'].find { it.properties['name'] == 'answer' }
                    def type = root.descendants.find { it.concept.qualifiedName.endsWith('.ClassifierType') }
                    def module = model.module
                    def oldModel = module.createModel('old.model')
                    def newModel = module.createModel('new.model')
                    def old = mops.parsing.java.addJavaClassesFromString(oldModel, 'public class OldTarget {}').nodes[0]
                    def replacement = mops.parsing.java.addJavaClassesFromString(newModel, 'public class NewTarget {}').nodes[0]
                    type.references['classifier'] = old
                    new jetbrains.mps.smodel.ModelImports(model).addModelImport(oldModel.reference)
                    model.save()
                    [root: root, method: method, type: type, statements: method.child['body'], old: old, replacement: replacement]
                }
            """.trimIndent())).jsonObject
            val initialPid = daemonPid()
            val root = fixture.getValue("root").jsonPrimitive.content
            val replacements = mapOf(
                "sample.model" to "baselanguage.sandbox",
                "NODE_REF" to root,
                "CLASS_REF" to root,
                "TARGET_REF" to fixture.getValue("replacement").jsonPrimitive.content,
                "STATEMENTS_REF" to fixture.getValue("statements").jsonPrimitive.content,
                "OLD_REF" to fixture.getValue("old").jsonPrimitive.content,
                "NEW_REF" to fixture.getValue("replacement").jsonPrimitive.content,
            )
            val rows = Regex("""(?m)^\|\s*([^|]+?)\s*\|\s*`([^`]+)`\s*\|$""")
                .findAll(ExampleTopics.page("editing.nodes")).toList()
            assertEquals(19, rows.size, "Every node recipe needs an execution and assertion")
            for (row in rows) {
                val task = row.groupValues[1].trim()
                val substitutions = when (task) {
                    "Create a solution and model" -> replacements - "sample.model"
                    "Read one child", "Clear a child role" -> replacements +
                        ("NODE_REF" to fixture.getValue("method").jsonPrimitive.content)
                    "Resolve a reference target", "Change a reference target" -> replacements +
                        ("NODE_REF" to fixture.getValue("type").jsonPrimitive.content)
                    else -> replacements
                }
                val source = row.groupValues[2]
                val memberQuery = "project.read { mops.lookup.requireNode('$root').children['member'] }"
                val membersBefore = if (task == "Reverse ordered members") Json.parseToJsonElement(run(memberQuery)).jsonArray else null
                val result = run(source, substitutions)
                when (task) {
                    "List root names and references" -> assertContains(result, "Example")
                    "Read a node property" -> assertEquals("Example", result)
                    "Find a root with an exact name" -> assertEquals(root, result)
                    "List containment descendants" -> assertContains(result, "node")
                    "List ancestors" -> assertEquals("[]", result)
                    "List available property names" -> assertContains(result, "name")
                    "List child roles" -> assertContains(result, "member")
                    "List reference roles" -> Json.parseToJsonElement(result).jsonArray
                    "Read one child" -> assertEquals(fixture.getValue("statements").jsonPrimitive.content, result)
                    "Read ordered children in a role" -> assertTrue(Json.parseToJsonElement(result).jsonArray.size >= 2)
                    "Resolve a reference target" -> assertEquals(fixture.getValue("old").jsonPrimitive.content, result)
                    "Rename a node" -> assertEquals("Renamed", run("project.read { mops.lookup.requireNode('$root').properties['name'] }"))
                    "Change a reference target" -> assertEquals(fixture.getValue("replacement").jsonPrimitive.content,
                        run("project.read { mops.lookup.requireNode('${fixture.getValue("type").jsonPrimitive.content}').references['classifier'].targetNode }"))
                    "Clear a child role" -> assertEquals("true", run("project.read { mops.lookup.requireNode('${fixture.getValue("method").jsonPrimitive.content}').child['body'] == null }"))
                    "Reverse ordered members" -> assertEquals(membersBefore!!.reversed(), Json.parseToJsonElement(run(memberQuery)).jsonArray.toList())
                    "Create a class root" -> assertEquals("Example", run("project.read { mops.lookup.requireNode('$result').properties['name'] }"))
                    "Create a solution and model" -> assertContains(result, "sample.model")
                    "Render a node" -> assertContains(result, "Renamed")
                    "Inspect a runtime object's API" -> assertContains(result, "SNode")
                    else -> fail("Missing assertion for $task")
                }
            }
            for (source in blocks("editing.nodes")) {
                assertTrue(Json.parseToJsonElement(run(source, replacements + ("NODE_REF" to replacements.getValue("TARGET_REF")))).jsonArray.isNotEmpty())
            }
            val javaBlocks = blocks("editing.java")
            assertEquals(3, javaBlocks.size)
            var javaReplacements = replacements
            for ((index, source) in javaBlocks.withIndex()) {
                val result = Json.parseToJsonElement(run(source, javaReplacements)).jsonObject
                assertTrue(result.getValue("nodes").jsonArray.isNotEmpty())
                assertEquals(0, result.getValue("unresolved").jsonArray.size)
                if (index == 0) {
                    val classifier = result.getValue("nodes").jsonArray.single().jsonPrimitive.content
                    val statements = run("project.read { mops.lookup.requireNode('$classifier').children['member'].find { it.properties['name'] == 'answer' }.child['body'] }")
                    javaReplacements = replacements + mapOf("CLASS_REF" to classifier, "STATEMENTS_REF" to statements)
                }
            }
            val referenceBlocks = blocks("editing.references")
            assertEquals(2, referenceBlocks.size)
            run("project.command { mops.lookup.requireNode('${fixture.getValue("type").jsonPrimitive.content}').references['classifier'] = mops.lookup.requireNode('${fixture.getValue("old").jsonPrimitive.content}') }")
            assertEquals("1", run(referenceBlocks[0], replacements))
            run(referenceBlocks[1], replacements)
            val persisted = project.resolve("solutions/baselanguage.sandbox/models/baselanguage.sandbox.mps").readText()
            assertContains(persisted, "Renamed")
            assertContains(persisted, "new.model")
            assertFalse(persisted.contains("(old.model)"))
            val status = cli("daemon", "status")
            assertEquals(0, status.exitCode, status.output)
            assertContains(status.stdout, "running")
            assertEquals(initialPid, daemonPid(), "All recipes must reuse the same MPS daemon")
        } finally {
            cli("daemon", "stop")
        }
    }
}
