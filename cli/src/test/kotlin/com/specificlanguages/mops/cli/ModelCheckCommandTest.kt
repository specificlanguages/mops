package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock

/** Tests what `check model` selects. [CheckCommandTest] covers the output and exit code shared by every subcommand. */
@ResourceLock("system-streams")
class ModelCheckCommandTest {
    @Test
    fun `model check requires a model target`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("check", "model")
        }

        assertEquals(2, exitCode)
        assertContains(stderr, "Missing required parameter")
        assertContains(stderr, "MODEL_TARGET")
    }
}
