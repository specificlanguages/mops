package com.specificlanguages.mops.daemon

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.MopsBuild
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

class MopsDaemonVersionProviderTest {
    @Test
    fun `version option reports the shared build version`() {
        val output = tapSystemOut {
            assertEquals(0, CommandLine(MopsDaemonCommand()).execute("--version"))
        }

        assertEquals("mops-daemon ${MopsBuild.version}\n", output)
    }
}
