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

@ResourceLock("system-streams")
class ExternalDependencyReloadIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `unchanged consumer uses externally replaced dependency solution in same daemon`() {
        exerciseReplacement(library = false)
    }

    @Test
    fun `unchanged consumer uses externally replaced Java library directory in same daemon`() {
        exerciseReplacement(library = true)
    }

    @Test
    fun `unchanged consumer uses externally replaced Java library jar in same daemon`() {
        exerciseReplacement(library = true, archive = true)
    }

    @Test
    fun `unchanged consumer uses externally replaced Java library through a directory symlink`() {
        exerciseReplacement(library = true, symbolicLink = true)
    }

    private fun exerciseReplacement(library: Boolean, archive: Boolean = false, symbolicLink: Boolean = false) {
        val project = copyTestProject("mps-json", tempDir.resolve("project"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val dependencyId = UUID.randomUUID().toString()
        val dependency = project.resolve("solutions/reload.dependency").createDirectories()
        val consumer = project.resolve("solutions/reload.consumer").createDirectories()
        val dependencyOutput = if (library) tempDir.resolve("external-classes") else dependency.resolve("classes_gen")
        compile(dependencyOutput, "Value", "public static String value() { return \"before\"; }")
        val libraryPath = when {
            archive -> tempDir.resolve("external.jar")
            symbolicLink -> Files.createSymbolicLink(tempDir.resolve("external-link"), dependencyOutput)
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
              </facet></facets>
              $dependencies
              $libraries
            </solution>
        """.trimIndent()
        dependency.resolve("reload.dependency.msd").writeText(descriptor("reload.dependency", dependencyId,
            libraries = if (library) "<stubModelEntries><stubModelEntry path=\"$libraryPath\" /></stubModelEntries>" else ""))
        consumer.resolve("reload.consumer.msd").writeText(descriptor("reload.consumer", UUID.randomUUID().toString(),
            "<dependencies><dependency reexport=\"false\">$dependencyId(reload.dependency)</dependency></dependencies>"))
        val modules = project.resolve(".mps/modules.xml")
        modules.writeText(modules.readText().replace("</projectModules>", """
            <modulePath path="${'$'}PROJECT_DIR${'$'}/solutions/reload.dependency/reload.dependency.msd" />
            <modulePath path="${'$'}PROJECT_DIR${'$'}/solutions/reload.consumer/reload.consumer.msd" />
            </projectModules>
        """.trimIndent()))
        val program = tempDir.resolve("value.groovy").also { it.writeText("""
            project.read {
                def module = project.repository.modules.find { it.moduleName == 'reload.consumer' }
                project.getComponent(jetbrains.mps.classloading.ClassLoaderManager.class)
                    .getClassLoader(module).loadClass('Consumer').getMethod('value').invoke(null)
            }
        """.trimIndent()) }
        fun value(): String {
            val result = runCommandLine(project, "--daemon-home", daemonHome.pathString,
                *javaAndMpsHomeArgs(), "code", "run", program.pathString)
            assertEquals(0, result.exitCode, result.output)
            return result.stdout
        }
        try {
            assertContains(value(), "before")
            val store = DaemonRecordStore.forDaemonHome(daemonHome)
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
        } finally {
            stopDaemons(project, daemonHome)
        }
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
