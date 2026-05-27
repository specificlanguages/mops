package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.GsonCodec
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import java.time.Instant.now
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import kotlin.test.*

class ModelResaveCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `exports node json through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )

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
                "get-node",
                model.pathString,
                "2110045694544566904",
            )

            assertEquals(0, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            val node = GsonCodec.fromJson(stdout.toString(), Map::class.java)
            assertEquals(
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                node["model"],
            )
            assertEquals("jetbrains.mps.lang.structure.structure.ConceptDeclaration", node["concept"])
            assertEquals("2110045694544566904", node["id"])
            assertEquals("JsonFile", (node["properties"] as Map<*, *>)["name"])
            val references = node["references"] as List<*>
            val extendsReference = references.single { (it as Map<*, *>)["role"] == "extends" } as Map<*, *>
            val target = extendsReference["target"] as Map<*, *>
            assertEquals("r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)", target["model"])
            assertTrue(target["node"] is String)
            val children = node["children"] as List<*>
            assertTrue(children.isNotEmpty())
            assertTrue(children.any { (it as Map<*, *>)["role"] == "implements" })
        } finally {
            newCommandLine(
                workingDirectory = project,
            ).execute(
                "--daemon-home",
                daemonHome.pathString,
                "--mps-home", requiredProperty("test.mpsHome"),
                "daemon", "stop")

            waitForAllDaemons(daemonHome)
        }
    }

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
            newCommandLine(
                workingDirectory = project,
            ).execute(
                "--daemon-home",
                daemonHome.pathString,
                "--mps-home", requiredProperty("test.mpsHome"),
                "daemon", "stop")

            waitForAllDaemons(daemonHome)
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
            newCommandLine(
                workingDirectory = project,
            ).execute(
                "--daemon-home",
                daemonHome.pathString,
                "--mps-home", requiredProperty("test.mpsHome"),
                "daemon", "stop")

            waitForAllDaemons(daemonHome)
        }
    }

    private fun waitForAllDaemons(daemonHome: Path) {
        val recordStore = DaemonRecordStore.forDaemonHome(daemonHome)
        val deadline = now().plusSeconds(10)
        while (now() < deadline) {
            val anyAlive = recordStore.readAll().any { record ->
                val handle = ProcessHandle.of(record.record.pid).getOrNull()
                handle != null && handle.isAlive
            }

            if (anyAlive) {
                Thread.sleep(100)
            } else {
                return
            }
        }
    }

    private fun requiredProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "missing system property $name" }
}
