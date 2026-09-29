package com.specificlanguages.mops.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeModeIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `installed CLI executes model operations with the selected MPS distribution`() {
        checkCodeMode(Path.of(System.getProperty("test.mpsHome")))
    }

    @Test
    fun `installed CLI executes model operations without platform Groovy`() {
        val source = Path.of(System.getProperty("test.mpsHome"))
        val target = tempDir.resolve("mps-without-groovy")
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val relative = source.relativize(path)
                if (relative.toString().replace('\\', '/') != "lib/groovy.jar") {
                    val destination = target.resolve(relative)
                    if (path.isDirectory()) {
                        destination.createDirectories()
                    } else if (path.extension == "jar") {
                        // Runtime JARs are read-only. Hard links avoid duplicating the entire MPS distribution.
                        try {
                            Files.createLink(destination, path)
                        } catch (_: IOException) {
                            Files.copy(path, destination)
                        }
                    } else {
                        Files.copy(path, destination)
                    }
                }
            }
        }
        checkCodeMode(target)
    }

    private fun checkCodeMode(mpsHome: Path) {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val program = tempDir.resolve("model.groovy").also {
            it.writeText("""
                import groovy.lang.Closure
                import org.codehaus.groovy.control.CompilerConfiguration
                import org.jetbrains.mps.openapi.model.SModel

                def model = project.read { mops.lookup.requireModel('com.specificlanguages.json.structure') }
                assert model instanceof SModel
                assert Closure.class.classLoader.is(CompilerConfiguration.class.classLoader)
                assert Closure.class.protectionDomain.codeSource.location == CompilerConfiguration.class.protectionDomain.codeSource.location
                project.command {
                    def root = model.rootNodes.iterator().next()
                    root.properties['name'] = 'code-mode-test'
                }
                return project.read {
                    [model: model, name: model.rootNodes.iterator().next().properties['name'],
                     groovySource: CompilerConfiguration.class.protectionDomain.codeSource.location.toString()]
                }
            """.trimIndent())
        }

        fun cli(vararg args: String): CliResult {
            val install = Path.of(System.getProperty("test.cliInstall"))
            val java = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
            val output = tempDir.resolve("cli-output.txt")
            val builder = ProcessBuilder(
                java.pathString, "-cp", install.resolve("lib/*").pathString,
                "com.specificlanguages.mops.cli.MainKt",
                "--project-root", project.pathString,
                "--daemon-home", daemonHome.pathString,
                "--java-home", System.getProperty("test.jbrHome"),
                "--mps-home", mpsHome.pathString, *args,
            ).redirectErrorStream(true).redirectOutput(output.toFile())
            // The installed manifest must supply all daemon dependencies.
            listOf("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS").forEach(builder.environment()::remove)
            val process = builder.start()
            try {
                assertTrue(process.waitFor(3, TimeUnit.MINUTES), "CLI timed out: ${output.readText()}")
                return CliResult(process.exitValue(), output.readText(), "")
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }

        try {
            val run = cli("code", "run", program.pathString)
            assertEquals(0, run.exitCode, run.output)
            val result = Json.parseToJsonElement(run.stdout.trim()).jsonObject
            val mpsGroovy = mpsHome.resolve("lib/groovy.jar")
            val expectedGroovy = if (mpsGroovy.exists()) mpsGroovy else
                Path.of(System.getProperty("test.cliInstall"), "lib", "groovy-${System.getProperty("test.groovyVersion")}.jar")
            assertEquals(
                mapOf(
                    "model" to "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                    "name" to "code-mode-test",
                    "groovySource" to expectedGroovy.toRealPath().toUri().toURL().toString(),
                ),
                result.mapValues { it.value.jsonPrimitive.content },
            )

            val help = cli("code", "help", "mops")
            assertEquals(0, help.exitCode, help.output)
            assertContains(help.stdout, "mops.parsing.java")
            assertContains(help.stdout, "mops.search.eachUsageOf")

            assertEquals(0, cli("daemon", "stop").exitCode)
            program.writeText("return project.read { mops.lookup.requireModel('com.specificlanguages.json.structure').rootNodes.iterator().next().properties['name'] }")
            val persisted = cli("code", "run", program.pathString)
            assertEquals(0, persisted.exitCode, persisted.output)
            assertEquals("code-mode-test", persisted.stdout.trim())
        } finally {
            cli("daemon", "stop")
        }
    }
}
