package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsResult
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelResaveSemanticsTest {

    @Test
    fun `restores resolve attributes`() {
        lateinit var original: String

        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val model = project.resolve(STRUCTURE_MODEL_PATH)
                original = model.readText()
                assertTrue(original.contains(""" resolve=""""), "fixture should contain resolve attributes")
                model.writeText(original.replace(Regex(""" resolve="[^"]*"""")) { "" })
                assertFalse(model.readText().contains(""" resolve=""""), "test setup should remove resolve attributes")
            },
        ) { mpsAccess, projectPath ->
            val model = projectPath.resolve(STRUCTURE_MODEL_PATH)

            val result = mpsAccess.write { resave(model.pathString) }

            assertOk(result)
            assertEquals(original, model.readText())
        }
    }

    @Test
    fun `reports an unknown model target as not found`() {
        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                project.resolve("not-a-project-model.mps").writeText("<model />")
            },
        ) { mpsAccess, projectPath ->
            val unknownModel = projectPath.resolve("not-a-project-model.mps")

            val result = mpsAccess.write { resave(unknownModel.pathString) }

            assertEquals(
                MpsResult.Error(
                    code = MpsErrorCode.MODEL_NOT_FOUND,
                    message = "model not found: ${unknownModel.pathString}",
                ),
                result,
            )
        }
    }

    private companion object {
        const val STRUCTURE_MODEL_PATH = "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
    }
}
