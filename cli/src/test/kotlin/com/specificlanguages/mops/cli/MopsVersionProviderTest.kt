package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.MopsBuild
import kotlin.test.Test
import kotlin.test.assertEquals

class MopsVersionProviderTest {
    @Test
    fun `version option reports the shared build version`() {
        val output = tapSystemOut {
            assertEquals(0, newCommandLine().execute("--version"))
        }

        assertEquals("mops ${MopsBuild.version}\n", output)
    }
}
