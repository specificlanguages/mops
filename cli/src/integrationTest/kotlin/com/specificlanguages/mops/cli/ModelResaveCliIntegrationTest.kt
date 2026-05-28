package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class ModelResaveCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `restores resolve attributes through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val original = model.readText()
        assertTrue(original.contains(""" resolve=""""), "fixture should contain resolve attributes")
        model.writeText(original.replace(Regex(""" resolve="[^"]*"""")) { "" })
        assertFalse(model.readText().contains(""" resolve=""""), "test setup should remove resolve attributes")

        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        try {
            val exitCode = newCommandLine(
                workingDirectory = project,
            ).also {
                it.out = PrintWriter(stdout, true)
                it.err = PrintWriter(stderr, true)
            }.execute(
                "--daemon-home",
                daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "model",
                "resave",
                model.pathString,
            )

            assertEquals(0, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            assertContains(stdout.toString(), "Model resaved successfully: ${model.toRealPath().toRealPath()}")
            assertEquals(original, model.readText())
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `reports daemon errors on failure`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val unknownModel = project.resolve("not-a-loaded-model.mps")
        unknownModel.writeText("<model />")

        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        try {
            val exitCode = newCommandLine(
                workingDirectory = project,
            ).also {
                it.out = PrintWriter(stdout, true)
                it.err = PrintWriter(stderr, true)
            }.execute(
                "--daemon-home",
                daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "model",
                "resave",
                unknownModel.pathString,
            )

            assertEquals(1, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            assertContains(stderr.toString(), "model not found: ${unknownModel.toRealPath()}")
            assertFalse(stdout.toString().contains("Model resaved successfully"))
        } finally {
            stopDaemons(project, daemonHome)
        }
    }
}
