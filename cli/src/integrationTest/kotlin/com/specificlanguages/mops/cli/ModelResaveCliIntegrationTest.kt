package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

@ResourceLock("system-streams")
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

        try {
            val result = runCommandLine(
                project,
                "--daemon-home",
                daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "model",
                "resave",
                model.pathString,
            )

            assertEquals(0, result.exitCode, result.output)
            assertContains(result.stdout, "Model resaved successfully: ${model.toRealPath().toRealPath()}")
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

        try {
            val result = runCommandLine(
                project,
                "--daemon-home",
                daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "model",
                "resave",
                unknownModel.pathString,
            )

            assertEquals(1, result.exitCode, result.output)
            assertContains(result.stderr, "model not found: ${unknownModel.toRealPath()}")
            assertFalse(result.stdout.contains("Model resaved successfully"))
        } finally {
            stopDaemons(project, daemonHome)
        }
    }
}
