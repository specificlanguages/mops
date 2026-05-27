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

class ModelGetNodeCliIntegrationTest {
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
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `accepts compact regular node id through daemon`() {
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
                "1P8oQ4NaXDS",
            )

            assertEquals(0, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            val node = GsonCodec.fromJson(stdout.toString(), Map::class.java)
            assertEquals("2110045694544566904", node["id"])
            assertEquals("JsonFile", (node["properties"] as Map<*, *>)["name"])
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `accepts serialized model reference as model target through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val modelReference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"

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
                modelReference,
                "2110045694544566904",
            )

            assertEquals(0, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            val node = GsonCodec.fromJson(stdout.toString(), Map::class.java)
            assertEquals(modelReference, node["model"])
            assertEquals("JsonFile", (node["properties"] as Map<*, *>)["name"])
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `accepts serialized node reference through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"

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
                nodeReference,
            )

            assertEquals(0, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            val node = GsonCodec.fromJson(stdout.toString(), Map::class.java)
            assertEquals("2110045694544566904", node["id"])
            assertEquals("JsonFile", (node["properties"] as Map<*, *>)["name"])
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `omits parent role from addressed non-root node`() {
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
                "1P8oQ4NaXDT",
            )

            assertEquals(0, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            val node = GsonCodec.fromJson(stdout.toString(), Map::class.java)
            assertEquals(
                "jetbrains.mps.lang.structure.structure.InterfaceConceptReference",
                node["concept"],
            )
            assertFalse(node.containsKey("role"))
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `fails instead of guessing when model target is ambiguous`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val duplicate = model.resolveSibling("duplicate.structure.mps")
        duplicate.writeText(
            model.readText().replace(
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                "r:11111111-2222-4333-8444-555555555555(com.specificlanguages.json.structure)",
            ),
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
                "com.specificlanguages.json.structure",
                "2110045694544566904",
            )

            assertNotEquals(0, exitCode, "CLI output:\n${stdout}\nCLI error:\n${stderr}")
            assertContains(stderr.toString(), "ambiguous model target")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun stopDaemons(project: Path, daemonHome: Path) {
        newCommandLine(
            workingDirectory = project,
        ).execute(
            "--daemon-home",
            daemonHome.pathString,
            "--mps-home", requiredProperty("test.mpsHome"),
            "daemon", "stop")

        waitForAllDaemons(daemonHome)
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
