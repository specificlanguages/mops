package com.specificlanguages.mops.cli

import groovy.json.JsonSlurper
import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GroovyShell
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

@ResourceLock("system-streams")
@Tag("smoke")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BundledExamplesIntegrationTest {
    private lateinit var tempDir: Path

    private lateinit var project: Path
    private lateinit var home: Path
    private lateinit var cli: InstalledCli
    private lateinit var initialPid: String

    @BeforeAll
    fun startDaemon(@TempDir(cleanup = CleanupMode.ON_SUCCESS) directory: Path) {
        tempDir = directory
        project = copyTestProject("base-language-sandbox", tempDir.resolve("project"))
        home = tempDir.resolve("daemon-home").createDirectories()
        cli = InstalledCli(project, home, tempDir)
        val result = cli.run("daemon", "ping")
        assertEquals(0, result.exitCode, result.output)
        initialPid = daemonPid()
    }

    @AfterAll
    fun stopDaemon() {
        if (::cli.isInitialized) cli.run("daemon", "stop")
    }

    @TestFactory
    fun `bundled examples satisfy their specifications`(): List<DynamicTest> {
        val examples = Json.parseToJsonElement(resource("examples.json")).jsonArray
            .map { it.jsonObject }.filter { it["kind"]?.jsonPrimitive?.content == "groovy" }
        assertTrue(examples.any { "verify" in it }, "No executable examples in the catalog")
        return examples.mapIndexed { index, example ->
            val name = listOfNotNull(example["topic"], example["group"], example["title"] ?: example["id"])
                .joinToString("/") { it.jsonPrimitive.content }
            DynamicTest.dynamicTest(name) {
                Assumptions.assumeTrue("verify" in example, example["untested"]?.jsonPrimitive?.content)
                val modelName = "examples.case$index"
                val fixtureData = JsonSlurper().parseText(run(resource("fixture.groovy").replace("CASE_MODEL_NAME", modelName))) as Map<*, *>
                val fixture = fixtureData.entries.associate { it.key.toString() to it.value }
                val substitutions = mapOf(
                    "sample.model" to fixture.getValue("model").toString(),
                    "old.model" to fixture.getValue("oldModel").toString(),
                    "new.model" to fixture.getValue("newModel").toString(),
                    "NODE_REF" to fixture.getValue("root").toString(),
                    "CLASS_REF" to fixture.getValue("root").toString(),
                    "TYPE_REF" to fixture.getValue("type").toString(),
                    "STATEMENTS_REF" to fixture.getValue("statements").toString(),
                    "TARGET_REF" to fixture.getValue("replacement").toString(),
                    "OLD_REF" to fixture.getValue("old").toString(),
                    "NEW_REF" to fixture.getValue("replacement").toString(),
                ) + example["bindings"]?.jsonObject.orEmpty().mapValues { (_, selector) ->
                    val key = selector.jsonPrimitive.content
                    (fixture[key] ?: key).toString()
                }
                fun execute(source: String): String = run(substitutions.entries.fold(source) { text, (key, value) ->
                    text.replace(key, value)
                })
                val before = example["before"]?.jsonPrimitive?.content?.let(::execute)
                val output = execute(example.getValue("code").jsonPrimitive.content)
                val result = if (example["output"]?.jsonPrimitive?.content == "text") output else JsonSlurper().parseText(output)
                val modelFile = Files.walk(project).use { paths ->
                    paths.filter { it.fileName.toString() == "$modelName.mps" }.findFirst().orElseThrow().toFile()
                }
                val runClosure = object : Closure<String>(this) {
                    fun doCall(source: String): String = execute(source)
                }
                GroovyShell(Binding(mapOf("result" to result, "fixture" to fixture, "run" to runClosure,
                    "before" to before, "modelFile" to modelFile)))
                    .evaluate(example.getValue("verify").jsonPrimitive.content, "$name.verify.groovy")
                assertEquals(initialPid, daemonPid(), "All examples must reuse the same MPS daemon")
            }
        }
    }

    private fun run(source: String): String {
        val program = tempDir.resolve("example.groovy")
        program.writeText(source)
        val result = cli.run("code", "run", program.pathString)
        assertEquals(0, result.exitCode, "Program:\n$source\n${result.output}")
        return result.stdout.trim()
    }

    private fun daemonPid(): String = Files.walk(home).use { paths ->
        val record = paths.filter { it.fileName.toString() == "daemon.json" }.findFirst().orElseThrow()
        Json.parseToJsonElement(record.readText()).jsonObject.getValue("pid").jsonPrimitive.content
    }

    private fun resource(name: String): String = checkNotNull(javaClass.getResourceAsStream("/$name")) { name }
        .bufferedReader().use { it.readText() }
}
