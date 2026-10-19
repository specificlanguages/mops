package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore

import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.CRC32
import javax.tools.ToolProvider
import kotlin.io.path.*
import kotlin.test.*

/**
 * Each scenario adds its own pair of solutions, `reload.<scenario>.dependency` and `reload.<scenario>.consumer`, to the
 * shared `mps-json` copy from [SharedDaemons], and keeps the class files and libraries they load in a directory of the
 * copy. The daemon may already be running when a scenario adds its solutions. That is safe because the daemon loads
 * the "before" value first, and only then does the scenario replace the dependency and expect the "after!" value.
 */
@ResourceLock("system-streams")
class ExternalDependencyReloadIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `unchanged consumer uses externally replaced dependency solution in same daemon`() {
        exerciseReplacement("solution", library = false)
    }

    @Test
    fun `unchanged consumer uses externally replaced Java library directory in same daemon`() {
        exerciseReplacement("directory", library = true)
    }

    @Test
    fun `unchanged consumer uses externally replaced Java library jar in same daemon`() {
        exerciseReplacement("jar", library = true, archive = true)
    }

    @Test
    fun `unchanged consumer uses externally replaced Java library through a directory symlink`() {
        exerciseReplacement("symlink", library = true, symbolicLink = true)
    }

    private fun exerciseReplacement(
        scenario: String,
        library: Boolean,
        archive: Boolean = false,
        symbolicLink: Boolean = false,
    ) {
        val shared = SharedDaemons.project("mps-json")
        val project = shared.root
        // The daemon outlives this test's temporary directory, so files that modules load stay inside the copy.
        val external = project.resolve("reload-$scenario").createDirectories()
        val dependencyName = "reload.$scenario.dependency"
        val consumerName = "reload.$scenario.consumer"
        val dependencyId = UUID.randomUUID().toString()
        val dependency = project.resolve("solutions/$dependencyName").createDirectories()
        val consumer = project.resolve("solutions/$consumerName").createDirectories()
        val dependencyOutput = if (library) external.resolve("external-classes") else dependency.resolve("classes_gen")
        compile(dependencyOutput, "Value", "public static String value() { return \"before\"; }")
        val libraryPath = when {
            archive -> external.resolve("external.jar")
            symbolicLink -> Files.createSymbolicLink(external.resolve("external-link"), dependencyOutput)
            else -> dependencyOutput
        }
        fun packLibrary() {
            if (!archive) return
            val bytes = dependencyOutput.resolve("Value.class").readBytes()
            JarOutputStream(Files.newOutputStream(libraryPath)).use { jar ->
                jar.putNextEntry(JarEntry("Value.class").apply {
                    method = JarEntry.STORED
                    size = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                    time = 0
                })
                jar.write(bytes)
                jar.closeEntry()
            }
        }
        packLibrary()
        compile(consumer.resolve("classes_gen"), "Consumer",
            "public static String value() { return Value.value(); }", dependencyOutput)
        fun descriptor(name: String, id: String, dependencies: String = "", libraries: String = "") = """
            <solution name="$name" uuid="$id" moduleVersion="0">
              <facets><facet type="java" compile="ext" classes="mps" ext="yes">
                <classes generated="true" path="${'$'}{module}/classes_gen" />
                $libraries
              </facet></facets>
              $dependencies
            </solution>
        """.trimIndent()
        dependency.resolve("$dependencyName.msd").writeText(descriptor(dependencyName, dependencyId,
            libraries = if (library) "<library location=\"$libraryPath\" />" else ""))
        consumer.resolve("$consumerName.msd").writeText(descriptor(consumerName, UUID.randomUUID().toString(),
            "<dependencies><dependency reexport=\"false\">$dependencyId($dependencyName)</dependency></dependencies>"))
        val modules = project.resolve(".mps/modules.xml")
        modules.writeText(modules.readText().replace("</projectModules>", """
            <modulePath path="${'$'}PROJECT_DIR${'$'}/solutions/$dependencyName/$dependencyName.msd" />
            <modulePath path="${'$'}PROJECT_DIR${'$'}/solutions/$consumerName/$consumerName.msd" />
            </projectModules>
        """.trimIndent()))
        val program = tempDir.resolve("value.groovy").also { it.writeText("""
            project.read {
                def module = project.repository.modules.find { it.moduleName == '$consumerName' }
                project.getComponent(jetbrains.mps.classloading.ClassLoaderManager.class)
                    .getClassLoader(module).loadClass('Consumer').getMethod('value').invoke(null)
            }
        """.trimIndent()) }
        fun value(): String {
            val result = shared.run("code", "run", program.pathString)
            assertEquals(0, result.exitCode, result.output)
            return result.stdout
        }
        assertContains(value(), "before")
        val store = DaemonRecordStore.forDaemonHome(shared.daemonHome)
        val pid = assertNotNull(store.read(project)).record.pid
        val consumerBytes = consumer.resolve("classes_gen/Consumer.class").readBytes()
        val artifact = if (archive) libraryPath else dependencyOutput.resolve("Value.class")
        val stamp = Files.getLastModifiedTime(artifact)
        val size = Files.size(artifact)
        compile(dependencyOutput, "Value", "public static String value() { return \"after!\"; }")
        packLibrary()
        assertEquals(size, Files.size(artifact))
        Files.setLastModifiedTime(artifact, stamp)
        assertContains(value(), "after!")
        assertContentEquals(consumerBytes, consumer.resolve("classes_gen/Consumer.class").readBytes())
        assertEquals(pid, assertNotNull(store.read(project)).record.pid)
    }

    private fun compile(output: Path, name: String, body: String, dependency: Path? = null) {
        output.createDirectories()
        val source = tempDir.resolve("$name.java").also { it.writeText("public class $name { $body }") }
        val args = mutableListOf("--release", "17", "-d", output.pathString)
        if (dependency != null) args.addAll(listOf("-classpath", dependency.pathString))
        args.add(source.pathString)
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, *args.toTypedArray()))
    }
}
