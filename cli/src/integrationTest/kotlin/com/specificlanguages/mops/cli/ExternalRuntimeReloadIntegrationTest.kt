package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.*
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*
import kotlin.test.*

@ResourceLock("system-streams")
class ExternalRuntimeReloadIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `external compiled language replacement updates runtime in the same daemon`() {
        val builder = copyTestProject("mps-json", tempDir.resolve("builder"))
        val target = tempDir.resolve("target")
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        fun command(project: Path, vararg args: String) = runCommandLine(
            project, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(), *args,
        )
        fun succeed(project: Path, vararg args: String): CliResult = command(project, *args).also {
            assertEquals(0, it.exitCode, it.output)
        }
        fun setProperty(name: String, value: String) {
            val batch = tempDir.resolve("property.json")
            batch.writeText(ProtocolJson.encodeBatch(EditBatch(listOf(EditOperation.SetProperty(
                target = EditTarget.NodeReference(
                    "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
                ),
                name = name, value = value,
            )))))
            succeed(builder, "edit", "model", "--file", batch.pathString)
        }
        try {
            setProperty("conceptAlias", "OldAlias")
            succeed(builder, "make", "module", "json.sandbox")
            copyTree(builder, target)
            val first = succeed(target, "find", "instances", "--json", "com.specificlanguages.json.structure.JsonFile")
            assertTrue((ProtocolJson.decodeResponse(first.stdout) as FindInstancesResponse).nodes.isNotEmpty())
            val store = DaemonRecordStore.forDaemonHome(daemonHome)
            val pid = assertNotNull(store.read(target)).record.pid

            setProperty("name", "ReloadedJsonFile")
            succeed(builder, "make", "module", "json.sandbox")
            copyTree(builder.resolve("languages"), target.resolve("languages"))

            val updated = succeed(target, "find", "instances", "--json", "com.specificlanguages.json.structure.ReloadedJsonFile")
            assertTrue((ProtocolJson.decodeResponse(updated.stdout) as FindInstancesResponse).nodes.isNotEmpty())
            val superseded = command(target, "find", "instances", "com.specificlanguages.json.structure.JsonFile")
            assertEquals(1, superseded.exitCode, superseded.output)
            assertContains(superseded.output, "was not found")
            assertContains(superseded.output, "which is loaded")

            val program = tempDir.resolve("alias.groovy").also {
                it.writeText("project.read { mops.lookup.requireConceptByName('com.specificlanguages.json.structure.ReloadedJsonFile').conceptAlias }")
            }
            assertContains(succeed(target, "code", "run", program.pathString).stdout, "OldAlias")
            val stamps = Files.walk(target.resolve("languages")).use { paths ->
                paths.filter { it.isRegularFile() && it.any { segment -> segment.toString() == "classes_gen" } }
                    .toList().associateWith { Files.size(it) to Files.getLastModifiedTime(it) }
            }
            setProperty("conceptAlias", "NewAlias")
            succeed(builder, "make", "module", "json.sandbox")
            copyTree(builder.resolve("languages"), target.resolve("languages"))
            stamps.forEach { (path, stamp) ->
                assertEquals(stamp.first, Files.size(path), "compiled output size must be preserved: $path")
                Files.setLastModifiedTime(path, stamp.second)
            }
            val replacedFiles = Files.walk(target.resolve("languages")).use { paths ->
                paths.filter { it.isRegularFile() && it.any { segment -> segment.toString() == "classes_gen" } }
                    .toList().toSet()
            }
            assertEquals(stamps.keys, replacedFiles, "compiled output paths must be preserved")
            assertContains(succeed(target, "code", "run", program.pathString).stdout, "NewAlias")
            assertEquals(pid, assertNotNull(store.read(target)).record.pid)
        } finally {
            stopDaemons(builder, daemonHome)
            if (target.exists()) stopDaemons(target, daemonHome)
        }
    }

    private fun copyTree(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path))
                if (path.isDirectory()) destination.createDirectories()
                else if (!destination.exists() || Files.mismatch(path, destination) != -1L) {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
