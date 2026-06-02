package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.MpsNodeJson
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

        try {
            val result = runGetNode(
                project,
                daemonHome,
                model.pathString,
                "2110045694544566904",
            )

            assertEquals(0, result.exitCode, result.output)
            val node = nodeJson(result)
            assertEquals(
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                node.model,
            )
            assertEquals("jetbrains.mps.lang.structure.structure.ConceptDeclaration", node.concept)
            assertEquals("2110045694544566904", node.id)
            assertEquals("JsonFile", propertyValue(node, "name"))
            val extendsReference = requireNotNull(node.references).single { it.role == "extends" }
            assertEquals(
                "r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)",
                extendsReference.target.model,
            )
            assertNotNull(extendsReference.target.node)
            assertTrue(requireNotNull(node.children).isNotEmpty())
            assertTrue(requireNotNull(node.children).any { it.role == "implements" })
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

        try {
            val result = runGetNode(
                project,
                daemonHome,
                model.pathString,
                "1P8oQ4NaXDS",
            )

            assertEquals(0, result.exitCode, result.output)
            val node = nodeJson(result)
            assertEquals("2110045694544566904", node.id)
            assertEquals("JsonFile", propertyValue(node, "name"))
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `accepts serialized model reference as model target through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val modelReference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"

        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runGetNode(
                project,
                daemonHome,
                modelReference,
                "2110045694544566904",
            )

            assertEquals(0, result.exitCode, result.output)
            val node = nodeJson(result)
            assertEquals(modelReference, node.model)
            assertEquals("JsonFile", propertyValue(node, "name"))
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `matches model target by model name value instead of long name`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        model.resolveSibling("com.specificlanguages.json.structure@tests.mps").writeText(
            model.readText().replace(
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                "r:11111111-2222-4333-8444-555555555555(com.specificlanguages.json.structure@tests)",
            ),
        )

        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val unstereotypedResult = runGetNode(
                project,
                daemonHome,
                "com.specificlanguages.json.structure",
                "2110045694544566904",
            )

            assertEquals(0, unstereotypedResult.exitCode, unstereotypedResult.output)
            assertEquals(
                "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                nodeJson(unstereotypedResult).model,
            )

            val stereotypedResult = runGetNode(
                project,
                daemonHome,
                "com.specificlanguages.json.structure@tests",
                "2110045694544566904",
            )

            assertEquals(0, stereotypedResult.exitCode, stereotypedResult.output)
            assertEquals(
                "r:11111111-2222-4333-8444-555555555555(com.specificlanguages.json.structure@tests)",
                nodeJson(stereotypedResult).model,
            )
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

        try {
            val result = runGetNode(
                project,
                daemonHome,
                nodeReference,
            )

            assertEquals(0, result.exitCode, result.output)
            val node = nodeJson(result)
            assertEquals("2110045694544566904", node.id)
            assertEquals("JsonFile", propertyValue(node, "name"))
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

        try {
            val result = runGetNode(
                project,
                daemonHome,
                model.pathString,
                "1P8oQ4NaXDT",
            )

            assertEquals(0, result.exitCode, result.output)
            val node = nodeJson(result)
            assertEquals(
                "jetbrains.mps.lang.structure.structure.InterfaceConceptReference",
                node.concept,
            )
            assertNull(node.role)
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

        try {
            val result = runGetNode(
                project,
                daemonHome,
                "com.specificlanguages.json.structure",
                "2110045694544566904",
            )

            assertNotEquals(0, result.exitCode, result.output)
            assertContains(result.stderr, "ambiguous model target")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun runGetNode(project: Path, daemonHome: Path, vararg nodeTarget: String): CliResult =
        runCommandLine(
            project,
            "--daemon-home",
            daemonHome.pathString,
            *javaAndMpsHomeArgs(),
            "model",
            "get-node",
            *nodeTarget,
        )

    private fun nodeJson(result: CliResult): MpsNodeJson =
        GsonCodec.fromJson(result.stdout, MpsNodeJson::class.java)

    private fun propertyValue(node: MpsNodeJson, name: String): String? =
        requireNotNull(node.properties).single { it.name == name }.value
}
