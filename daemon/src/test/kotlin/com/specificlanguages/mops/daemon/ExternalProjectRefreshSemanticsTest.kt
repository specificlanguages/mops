package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget
import jetbrains.mps.project.MPSProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/** A refresh sees an external edit of a model file made immediately before it. */
class ExternalProjectRefreshSemanticsTest {

    @Test
    fun `refresh right after an external edit sees the edit of a project model`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            exerciseEdits(project, projectPath.resolve(STRUCTURE_MODEL_PATH), "JsonFile") {
                getNode(NodeTarget.InModel(STRUCTURE_MODEL_NAME, JSON_FILE_NODE_ID))
            }
        }
    }

    @Test
    fun `refresh right after an external edit sees the edit of a library model outside the project folder`() {
        lateinit var library: Path
        SharedMpsEnvironment.withOpenProjectCopy(LIBRARY_PROJECT, prepare = { project ->
            library = Files.move(project.resolve("libs"), project.resolveSibling("shared"))
            val libraries = project.resolve(".mps/libraries.xml")
            libraries.writeText(libraries.readText().replace("${'$'}PROJECT_DIR${'$'}/libs", "${'$'}PROJECT_DIR${'$'}/../shared"))
        }) { project, _ ->
            exerciseEdits(project, library.resolve("library/models/values.mps"), "LibraryValue") {
                getNode(NodeTarget.InModel(LIBRARY_MODEL_NAME, LIBRARY_ROOT_NODE_ID))
            }
        }
    }

    private fun exerciseEdits(
        project: MPSProject,
        model: Path,
        originalName: String,
        node: MpsRead.() -> MpsNodeJson,
    ) {
        val access = SharedMpsEnvironment.mpsAccess(project)
        val refresh = ExternalProjectRefresh(project)
        refresh.refresh()
        var previousName = originalName
        repeat(EDITS) { index ->
            val name = "$originalName$index"
            model.writeText(model.readText().replace("value=\"$previousName\"", "value=\"$name\""))
            previousName = name

            refresh.refresh()

            assertEquals(name, propertyValue(access.read(node), "name"), "edit $index is visible after the refresh")
        }
    }

    private companion object {
        const val EDITS = 20

        const val STRUCTURE_MODEL_NAME = "com.specificlanguages.json.structure"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
        const val JSON_FILE_NODE_ID = "2110045694544566904"

        const val LIBRARY_PROJECT = "project-library"
        const val LIBRARY_MODEL_NAME = "mops.library.values"
        const val LIBRARY_ROOT_NODE_ID = "4096"
    }
}
