package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.EditApplyResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@ResourceLock("system-streams")
class EditApplyCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `set property by node reference persists through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val targetReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"

        try {
            val apply = runEditApply(
                project,
                daemonHome,
                EditBatch(
                    operations = listOf(
                        EditOperation.SetProperty(
                            target = EditTarget.NodeReference(targetReference),
                            name = "name",
                            value = "RenamedJsonFile",
                        ),
                    ),
                ),
            )

            assertEquals(0, apply.exitCode, apply.output)
            assertEquals(
                EditApplyResponse(created = emptyMap(), violations = emptyList()),
                GsonCodec.fromJson(apply.stdout, EditApplyResponse::class.java),
            )

            val reread = runGetNode(project, daemonHome, model.pathString, "2110045694544566904")
            assertEquals(0, reread.exitCode, reread.output)
            assertEquals("RenamedJsonFile", propertyValue(nodeJson(reread), "name"))
            assertContains(model.readText(), """value="RenamedJsonFile"""")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `clear property by model target persists through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val apply = runEditApply(
                project,
                daemonHome,
                EditBatch(
                    operations = listOf(
                        EditOperation.SetProperty(
                            target = EditTarget.InModel(
                                modelTarget = model.pathString,
                                nodeId = "2110045694544566904",
                            ),
                            name = "conceptAlias",
                        ),
                    ),
                ),
            )

            assertEquals(0, apply.exitCode, apply.output)
            val reread = runGetNode(project, daemonHome, model.pathString, "2110045694544566904")
            assertEquals(0, reread.exitCode, reread.output)
            assertNull(propertyValueOrNull(nodeJson(reread), "conceptAlias"))
            assertContains(model.readText(), """value="JsonFile"""")
            assertNotEquals(true, model.readText().contains("""value="JSON File""""))
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `failed later operation rolls back earlier property edit`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val targetReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"

        try {
            val apply = runEditApply(
                project,
                daemonHome,
                EditBatch(
                    operations = listOf(
                        EditOperation.SetProperty(
                            target = EditTarget.NodeReference(targetReference),
                            name = "name",
                            value = "ShouldRollback",
                        ),
                        EditOperation.SetProperty(
                            target = EditTarget.NodeReference(targetReference),
                            name = "doesNotExist",
                            value = "boom",
                        ),
                    ),
                ),
            )

            assertEquals(1, apply.exitCode, apply.output)
            assertContains(apply.stderr, "property not found: doesNotExist")
            val reread = runGetNode(project, daemonHome, model.pathString, "2110045694544566904")
            assertEquals(0, reread.exitCode, reread.output)
            assertEquals("JsonFile", propertyValue(nodeJson(reread), "name"))
            assertNotEquals(true, model.readText().contains("ShouldRollback"))
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `targeting non-editable model fails`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val existing = runGetNode(project, daemonHome, model.pathString, "2110045694544566904")
            assertEquals(0, existing.exitCode, existing.output)
            val extendsTarget = requireNotNull(nodeJson(existing).references)
                .single { it.role == "extends" }
                .target
            val libraryReference = "${extendsTarget.model}/${extendsTarget.node}"

            val apply = runEditApply(
                project,
                daemonHome,
                EditBatch(
                    operations = listOf(
                        EditOperation.SetProperty(
                            target = EditTarget.NodeReference(libraryReference),
                            name = "name",
                            value = "EditedBaseConcept",
                        ),
                    ),
                ),
            )

            assertEquals(1, apply.exitCode, apply.output)
            assertContains(apply.stderr, "not editable")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun runEditApply(project: Path, daemonHome: Path, batch: EditBatch): CliResult {
        val batchFile = tempDir.resolve("batch-${System.nanoTime()}.json")
        batchFile.writeText(GsonCodec.toJson(batch))
        return runCommandLine(
            project,
            "--daemon-home",
            daemonHome.pathString,
            *javaAndMpsHomeArgs(),
                "model",
                "edit",
                "--file",
                batchFile.pathString,
        )
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
        requireNotNull(propertyValueOrNull(node, name)) { "missing property $name in $node" }

    private fun propertyValueOrNull(node: MpsNodeJson, name: String): String? =
        node.properties?.singleOrNull { it.name == name }?.value
}
