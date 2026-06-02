package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.MpsListEntryJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@ResourceLock("system-streams")
class MpsListCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `lists current project entity through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(project, daemonHome, "--json", "--depth", "0")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("project", root.type)
            assertEquals("mps-json", root.name)
            assertEquals(null, root.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists project modules through daemon by default`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runListCommand(project, daemonHome, "ls", "--json")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            val modules = assertNotNull(root.children)
            val language = modules.single { it.name == "com.specificlanguages.json" }
            assertEquals("module", language.type)
            assertEquals("language", language.moduleKind)
            assertEquals("f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)", language.reference)
            assertNull(language.children)

            val solution = modules.single { it.name == "com.specificlanguages.json.build" }
            assertEquals("module", solution.type)
            assertEquals("solution", solution.moduleKind)
            assertEquals("84f0ad52-c7ca-45dd-99c5-9605c96bf808(com.specificlanguages.json.build)", solution.reference)
            assertNull(solution.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists repository entity through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(project, daemonHome, "--json", "--depth", "0", "/")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("repository", root.type)
            assertEquals("/", root.name)
            assertNull(root.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists repository modules through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runListCommand(project, daemonHome, "ls", "--json", "/")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("repository", root.type)
            val modules = assertNotNull(root.children)
            assertNotNull(modules.singleOrNull { it.name == "com.specificlanguages.json" })
            assertNotNull(modules.singleOrNull { it.name == "com.specificlanguages.json.build" })
            assertNotNull(modules.singleOrNull { it.name == "MPS.Core" })
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists models owned by project module through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(project, daemonHome, "--json", "com.specificlanguages.json")

            assertEquals(0, result.exitCode, result.output)
            val module = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("module", module.type)
            assertEquals("com.specificlanguages.json", module.name)
            assertEquals("language", module.moduleKind)
            assertEquals("f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)", module.reference)

            val models = assertNotNull(module.children)
            val structure = models.single { it.name == "com.specificlanguages.json.structure" }
            assertEquals("model", structure.type)
            assertEquals("r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)", structure.reference)
            assertNull(structure.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists root nodes owned by model path through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(
                project,
                daemonHome,
                "--json",
                "com.specificlanguages.json",
                "com.specificlanguages.json.structure",
            )

            assertEquals(0, result.exitCode, result.output)
            val model = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("model", model.type)
            assertEquals("com.specificlanguages.json.structure", model.name)
            assertEquals("r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)", model.reference)

            val roots = assertNotNull(model.children)
            val jsonFile = roots.single { it.name == "JsonFile" }
            assertEquals("root", jsonFile.type)
            assertEquals("jetbrains.mps.lang.structure.structure.ConceptDeclaration", jsonFile.concept)
            assertEquals("2110045694544566904", jsonFile.id)
            assertEquals(
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
                jsonFile.reference,
            )
            assertNull(jsonFile.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `expands dot-prefixed model suffix after module target through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val moduleTargets = listOf(
            "com.specificlanguages.json",
            "f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)",
        )

        try {
            for (moduleTarget in moduleTargets) {
                val result = runList(
                    project,
                    daemonHome,
                    "--json",
                    moduleTarget,
                    ".structure",
                    "JsonFile",
                )

                assertEquals(0, result.exitCode, result.output)
                val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
                assertEquals("root", root.type)
                assertEquals("JsonFile", root.name)
                assertEquals("2110045694544566904", root.id)
                assertNotNull(root.children)
            }
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists root nodes owned by serialized model reference through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val modelReference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"

        try {
            val result = runList(project, daemonHome, "--json", modelReference)

            assertEquals(0, result.exitCode, result.output)
            val model = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("model", model.type)
            assertEquals("com.specificlanguages.json.structure", model.name)
            assertEquals(modelReference, model.reference)

            val roots = assertNotNull(model.children)
            val jsonFile = roots.single { it.name == "JsonFile" }
            assertEquals("root", jsonFile.type)
            assertEquals("2110045694544566904", jsonFile.id)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists containment children owned by root node path through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(
                project,
                daemonHome,
                "--json",
                "com.specificlanguages.json",
                "com.specificlanguages.json.structure",
                "JsonFile",
            )

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("root", root.type)
            assertEquals("JsonFile", root.name)
            assertEquals("2110045694544566904", root.id)

            val children = assertNotNull(root.children)
            val implements = children.single { it.role == "implements" }
            assertEquals("node", implements.type)
            assertNull(implements.name)
            assertEquals(
                "jetbrains.mps.lang.structure.structure.InterfaceConceptReference",
                implements.concept,
            )
            assertNotNull(implements.id)
            assertNotNull(implements.reference)
            assertNull(implements.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists root node addressed by compact node id through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(
                project,
                daemonHome,
                "--json",
                "com.specificlanguages.json",
                "com.specificlanguages.json.structure",
                "1P8oQ4NaXDS",
            )

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("root", root.type)
            assertEquals("JsonFile", root.name)
            assertEquals("2110045694544566904", root.id)
            assertNotNull(root.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists root node addressed by serialized node reference through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"

        try {
            val result = runList(project, daemonHome, "--json", nodeReference)

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("root", root.type)
            assertEquals("JsonFile", root.name)
            assertEquals("2110045694544566904", root.id)
            assertEquals(nodeReference, root.reference)
            assertNotNull(root.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists containment children owned by child node path through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(
                project,
                daemonHome,
                "--json",
                "com.specificlanguages.json.build",
                "com.specificlanguages.json.build",
                "com.specificlanguages.json",
                "version",
            )

            assertEquals(0, result.exitCode, result.output)
            val node = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("node", node.type)
            assertNull(node.role)
            assertEquals("version", node.name)
            assertEquals("jetbrains.mps.build.structure.BuildVariableMacro", node.concept)
            assertNotNull(node.id)
            assertNotNull(node.reference)

            val children = assertNotNull(node.children)
            val initialValue = children.single { it.role == "initialValue" }
            assertEquals("node", initialValue.type)
            assertNull(initialValue.name)
            assertEquals(
                "jetbrains.mps.build.structure.BuildVariableMacroInitWithString",
                initialValue.concept,
            )
            assertNotNull(initialValue.id)
            assertNotNull(initialValue.reference)
            assertNull(initialValue.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `limits depth across module model root and child traversal through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(
                project,
                daemonHome,
                "--json",
                "--depth",
                "4",
                "com.specificlanguages.json.build",
            )

            assertEquals(0, result.exitCode, result.output)
            val module = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("module", module.type)

            val models = assertNotNull(module.children)
            val model = models.single { it.name == "com.specificlanguages.json.build" }
            val roots = assertNotNull(model.children)
            val buildProject = roots.single { it.name == "com.specificlanguages.json" }
            val rootChildren = assertNotNull(buildProject.children)
            val version = rootChildren.single { it.name == "version" }
            val versionChildren = assertNotNull(version.children)
            val initialValue = versionChildren.single { it.role == "initialValue" }
            assertNull(initialValue.children)
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
        val duplicateReference = "r:11111111-2222-4333-8444-555555555555(com.specificlanguages.json.structure)"
        model.resolveSibling("duplicate.structure.mps").writeText(
            model.readText().replace(
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                duplicateReference,
            ),
        )

        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(
                project,
                daemonHome,
                "--json",
                "com.specificlanguages.json",
                "com.specificlanguages.json.structure",
            )

            assertNotEquals(0, result.exitCode, result.output)
            assertContains(result.stderr, "ambiguous model target")
            assertContains(
                result.stderr,
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
            )
            assertContains(result.stderr, duplicateReference)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `fails instead of guessing when root node target is ambiguous`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        model.writeText(
            model.readText().replace(
                """<property role="TrG5h" value="JsonObject" />""",
                """<property role="TrG5h" value="JsonFile" />""",
            ),
        )

        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(
                project,
                daemonHome,
                "--json",
                "com.specificlanguages.json",
                "com.specificlanguages.json.structure",
                "JsonFile",
            )

            assertNotEquals(0, result.exitCode, result.output)
            assertContains(result.stderr, "ambiguous root node target JsonFile")
            assertContains(
                result.stderr,
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
            )
            assertContains(
                result.stderr,
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544567020",
            )
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `fails instead of guessing when child node target is ambiguous`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "solutions/com.specificlanguages.json.build/models/com.specificlanguages.json.build.mps",
        )
        model.writeText(
            model.readText().replace(
                """<property role="TrG5h" value="mps_home" />""",
                """<property role="TrG5h" value="version" />""",
            ),
        )

        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(
                project,
                daemonHome,
                "--json",
                "com.specificlanguages.json.build",
                "com.specificlanguages.json.build",
                "com.specificlanguages.json",
                "version",
            )

            assertNotEquals(0, result.exitCode, result.output)
            assertContains(result.stderr, "ambiguous child node target version")
            assertContains(
                result.stderr,
                "r:1044fb59-f691-4b27-8b09-aa9b966feb0e(com.specificlanguages.json.build)/48805613928016575",
            )
            assertContains(
                result.stderr,
                "r:1044fb59-f691-4b27-8b09-aa9b966feb0e(com.specificlanguages.json.build)/48805613928016630",
            )
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun runList(project: Path, daemonHome: Path, vararg args: String): CliResult =
        runListCommand(project, daemonHome, "list", *args)

    private fun runListCommand(project: Path, daemonHome: Path, command: String, vararg args: String): CliResult =
        runCommandLine(
            project,
            "--daemon-home",
            daemonHome.pathString,
            *javaAndMpsHomeArgs(),
            command,
            *args,
        )
}
