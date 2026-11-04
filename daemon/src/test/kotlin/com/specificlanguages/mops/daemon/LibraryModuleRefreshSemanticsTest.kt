package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.NodeTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryModuleRefreshSemanticsTest {

    @Test
    fun `refresh picks up an external edit of a model of a library module inside the project folder`() {
        exerciseLibraryEdit(libraryPath = "${'$'}PROJECT_DIR${'$'}/libs") { project -> project.resolve("libs") }
    }

    @Test
    fun `refresh picks up an external edit of a model of a library module outside the project folder`() {
        exerciseLibraryEdit(libraryPath = "${'$'}PROJECT_DIR${'$'}/../shared") { project ->
            Files.move(project.resolve("libs"), project.resolveSibling("shared"))
        }
    }

    private fun exerciseLibraryEdit(libraryPath: String, placeLibrary: (Path) -> Path) {
        lateinit var library: Path
        SharedMpsEnvironment.withProjectCopy(PROJECT, prepare = { project ->
            library = placeLibrary(project)
            val libraries = project.resolve(".mps/libraries.xml")
            libraries.writeText(libraries.readText().replace("${'$'}PROJECT_DIR${'$'}/libs", libraryPath))
        }) { access, _ ->
            assertEquals("LibraryValue", rootName(access))

            val model = library.resolve("library/models/values.mps")
            val original = model.readText()
            val edited = original.replace("""value="LibraryValue"""", """value="LibraryValueEdited"""")
            check(edited != original) { "expected to rewrite the LibraryValue name in $model" }
            model.writeText(edited)

            access.extra { refreshExternalChanges() }

            assertEquals("LibraryValueEdited", rootName(access), "refresh reloads the external library model edit")
        }
    }

    private fun rootName(access: MpsAccess): String =
        propertyValue(access.read { getNode(NodeTarget.InModel(MODEL_NAME, ROOT_NODE_ID)) }, "name")

    private companion object {
        const val PROJECT = "project-library"
        const val MODEL_NAME = "mops.library.values"
        const val ROOT_NODE_ID = "4096"
    }
}
