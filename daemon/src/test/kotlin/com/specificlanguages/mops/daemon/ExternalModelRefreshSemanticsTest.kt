package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalModelRefreshSemanticsTest {

    @Test
    fun `refresh picks up an external model edit without rebuilding or reopening the project`() {
        SharedMpsEnvironment.withProjectCopy { access, projectPath ->
            assertEquals("JsonFile", jsonFileName(access))

            val model = projectPath.resolve(STRUCTURE_MODEL_PATH)
            val original = model.readText()
            val edited = original.replace("""value="JsonFile"""", """value="JsonFileRenamed"""")
            check(edited != original) { "expected to rewrite the JsonFile name in $model" }
            model.writeText(edited)

            assertEquals("JsonFile", jsonFileName(access), "the loaded model stays unchanged before refresh")

            access.extra { refreshExternalChanges() }

            assertEquals("JsonFileRenamed", jsonFileName(access), "refresh reloads the external source edit")
        }
    }

    private fun jsonFileName(access: MpsAccess): String {
        val node = access.read {
            getNode(NodeTarget.InModel(STRUCTURE_MODEL_NAME, JSON_FILE_NODE_ID))
        }
        return propertyValue(node, "name")
    }

    private companion object {
        const val STRUCTURE_MODEL_NAME = "com.specificlanguages.json.structure"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
        const val JSON_FILE_NODE_ID = "2110045694544566904"
    }
}
