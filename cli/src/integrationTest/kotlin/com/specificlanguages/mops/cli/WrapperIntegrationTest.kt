package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.test.*

/**
 * Runs the installed `mops wrapper` on a Gradle build whose MPS project loads its project library through a path macro,
 * and on an MPS project without a Gradle build, then runs a command through the written `mopsw`.
 */
@ResourceLock("system-streams")
class WrapperIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `the wrapper passes a given or guessed path macro to the daemon`() {
        val repo = gradleBuild(tempDir.resolve("repo").createDirectories())
        repo.resolve(".git").createDirectory()
        val project = copyTestProject("project-library", repo.resolve("project"))
        val libraries = project.resolve(".mps/libraries.xml")
        libraries.writeText(libraries.readText().replace("\$PROJECT_DIR\$/libs", "\${mops.fixture.repo}/project/libs"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        val wrapper = repo.resolve("build/mopsw")
        val script = tempDir.resolve("library.groovy")
        script.writeText("project.read { mops.lookup.requireModule('mops.library') }\nreturn 'ok'\n")
        fun findLibraryThroughWrapper() {
            val result = runProcess(repo, listOf("sh", wrapper.pathString, "--daemon-home", home.pathString, "code", "run", script.pathString))
            assertEquals(0, result.exitCode, result.output)
            assertEquals("ok", result.stdout.trim(), result.output)
        }
        try {
            val guessed = runProcess(repo, installedMops("wrapper"))
            assertEquals(0, guessed.exitCode, guessed.output)
            assertContains(guessed.stderr, "Warning: path macro mops.fixture.repo used in .mps/libraries.xml")
            assertContains(guessed.stderr, "using the Git root $repo")
            assertContains(guessed.stderr, "rerun mops wrapper with --macro mops.fixture.repo=PATH")
            assertFalse(guessed.stdout.contains("Warning:"), guessed.output)
            assertContains(wrapper.readText(), "--macro='mops.fixture.repo=$repo'")
            findLibraryThroughWrapper()
            stopDaemons(project, home)

            // With the library moved away from the Git root, only the given value finds it.
            val elsewhere = tempDir.resolve("elsewhere/project").createDirectories().parent.toRealPath()
            project.resolve("libs").moveTo(elsewhere.resolve("project/libs"))
            val given = runProcess(repo, installedMops("wrapper", "--macro", "mops.fixture.repo=$elsewhere"))
            assertEquals(0, given.exitCode, given.output)
            assertFalse(given.stderr.contains("Warning:"), given.output)
            assertContains(wrapper.readText(), "--macro='mops.fixture.repo=$elsewhere'")
            findLibraryThroughWrapper()
        } finally { stopDaemons(project, home) }
    }

    @Test
    fun `given MPS and Java homes write a working wrapper without a Gradle build`() {
        val project = copyTestProject("project-library", tempDir.resolve("plain/project")).toRealPath()
        val home = tempDir.resolve("daemon-home").createDirectories()
        val script = tempDir.resolve("library.groovy")
        script.writeText("project.read { mops.lookup.requireModule('mops.library') }\nreturn 'ok'\n")
        try {
            val written = runProcess(
                project,
                installedMops(
                    "--mps-home", System.getProperty("test.mpsHome"),
                    "--java-home", System.getProperty("test.jbrHome"),
                    "wrapper",
                ),
            )
            assertEquals(0, written.exitCode, written.output)
            assertContains(written.stdout, "Wrapper: build/mopsw")
            val result = runProcess(
                project,
                listOf("sh", "build/mopsw", "--daemon-home", home.pathString, "code", "run", script.pathString),
            )
            assertEquals(0, result.exitCode, result.output)
            assertEquals("ok", result.stdout.trim(), result.output)
        } finally { stopDaemons(project, home) }
    }

    /** Writes a Gradle build at [root] whose runtime discovery finds the MPS and Java homes of the test run. */
    private fun gradleBuild(root: Path): Path {
        val mpsHome = Path.of(System.getProperty("test.mpsHome")).invariantSeparatorsPathString
        val java = Path.of(System.getProperty("test.jbrHome"), "bin", "java").invariantSeparatorsPathString
        root.resolve("settings.gradle").writeText("rootProject.name = 'wrapper-fixture'\n")
        root.resolve("build.gradle").writeText("""
            project.extensions.add('mpsSettings', [mpsHome: providers.provider { layout.projectDirectory.dir('$mpsHome') }])
            tasks.register('downloadJbr') { ext.javaExecutable = file('$java') }
        """.trimIndent())
        val repository = Path.of(System.getProperty("test.repoRoot"))
        root.resolve("gradle/wrapper").createDirectories()
        listOf("gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties").forEach {
            repository.resolve(it).copyTo(root.resolve(it))
        }
        return root.toRealPath()
    }

    /** The installed CLI with [args]; `mops wrapper` run this way writes wrappers that call the installed `bin/mops`. */
    private fun installedMops(vararg args: String): List<String> {
        val install = Path.of(System.getProperty("test.cliInstall"))
        return listOf(java().pathString, "-cp", install.resolve("lib").pathString + "/*", "com.specificlanguages.mops.cli.MainKt", *args)
    }

    private fun java(): Path = Path.of(System.getProperty("java.home"), "bin", "java")

    private fun runProcess(workingDirectory: Path, command: List<String>): CliResult {
        val name = "process-${System.nanoTime()}"
        val stdout = tempDir.resolve("$name-stdout.txt")
        val stderr = tempDir.resolve("$name-stderr.txt")
        val builder = ProcessBuilder(command).directory(workingDirectory.toFile())
            .redirectOutput(stdout.toFile()).redirectError(stderr.toFile())
        builder.environment()["JAVA_HOME"] = System.getProperty("java.home")
        val process = builder.start()
        try {
            val finished = process.waitFor(5, TimeUnit.MINUTES)
            val result = CliResult(if (finished) process.exitValue() else -1, stdout.readText(), stderr.readText())
            assertTrue(finished, "Command timed out: ${result.output}")
            return result
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
