package com.specificlanguages.mops.cli.homes

import kotlinx.serialization.json.*
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.*

internal class GradleHomeDiscovery {
    private class FileDiscoverer(start: Path) {
        private val ancestors = generateSequence(start) { it.parent }

        fun findDirectoryContaining(vararg regularFileNames: String): Path? =
            ancestors.firstOrNull { dir -> regularFileNames.any { dir.resolve(it).isRegularFile() } }
    }

    fun discover(start: Path, diagnostics: PrintWriter): List<HomeGuess> {
        val directory = start.toRealPath()
        require(directory.isDirectory()) { "Discovery path is not a directory: $directory" }

        val discoverer = FileDiscoverer(directory)

        val root = discoverer.findDirectoryContaining("settings.gradle", "settings.gradle.kts")
            ?: discoverer.findDirectoryContaining("build.gradle", "build.gradle.kts")
            ?: error("No Gradle build found at or above $directory.")
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val wrapper = FileDiscoverer(root).findDirectoryContaining(if (isWindows) "gradlew.bat" else "gradlew")
            ?: error("No Gradle wrapper found at or above $root. Add a wrapper to the build before running mops guess-command-line.")

        val temporary = Files.createTempDirectory("mops-guess-command-line-")
        try {
            val script = temporary.resolve("probe.gradle")
            javaClass.getResourceAsStream("guess-command-line.gradle")!!.use { Files.copy(it, script) }

            val report = temporary.resolve("report.json")
            val task = "mopsGuessCommandLine" + UUID.randomUUID().toString().replace("-", "")
            diagnostics.println("Inspecting Gradle build at $root. Runtime providers may download or extract distributions.")
            diagnostics.flush()
            val processArgs = listOf(
                "--project-dir", root.toString(),
                "--init-script", script.toString(),
                "--no-configuration-cache", "--no-configure-on-demand",
                "--quiet", "--console=plain",
                "-Dmops.guess.root=$root", "-Dmops.guess.task=$task",
                "-Dmops.guess.output=$report", ":$task",
            )
            val command = if (isWindows) listOf("cmd", "/c", wrapper.toString()) else listOf("sh", wrapper.toString())
            val process = ProcessBuilder(command + processArgs)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start()
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { diagnostics.println(it); diagnostics.flush() }
                }
                check(process.waitFor() == 0) { "Gradle runtime discovery failed; see Gradle diagnostics above." }
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
            check(report.isRegularFile()) { "Gradle did not produce a runtime discovery report." }
            return parseReport(report.readText())
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }

    internal fun parseReport(text: String): List<HomeGuess> {
        val report = Json.parseToJsonElement(text).jsonObject
        require(report.getValue("version").jsonPrimitive.int == 1) { "Unsupported runtime discovery report version." }
        return report.getValue("candidates").jsonArray.map { element ->
            val candidate = element.jsonObject
            fun path(key: String): Path? = candidate[key]?.jsonPrimitive?.contentOrNull?.let(Path::of)
            HomeGuess(
                projectDir = path("projectDir")!!,
                source = candidate.getValue("source").jsonPrimitive.content,
                mpsHome = path("mpsHome"),
                javaHome = path("javaHome"),
                diagnostics = candidate.getValue("diagnostics").jsonArray.map { it.jsonPrimitive.content },
            )
        }
    }

    internal fun select(candidates: List<HomeGuess>, start: Path): HomeGuess? =
        candidates.sortedWith(
            compareBy<HomeGuess> {
                when {
                    it.usableMps && it.usableJava -> 0
                    it.usableMps || it.usableJava -> 1
                    else -> 2
                }
            }.thenBy {
                if (start.startsWith(it.projectDir)) start.nameCount - it.projectDir.nameCount else Int.MAX_VALUE
            }.thenBy { it.projectDir.toString() }.thenBy {
                if (it.source.contains("mpsDefaults")) 0 else 1
            }.thenBy { it.source }
        ).distinctBy { it.mpsHome to it.javaHome }.firstOrNull()
}
