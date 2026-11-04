package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs `make module` through the CLI, launcher, and daemon against a project with a language and its sandbox. */
@ResourceLock("system-streams")
class MakeIntegrationTest {
    @Test
    fun `make module generates with several threads`() {
        val result = daemons.project("mps-json").run("make", "module", "--threads", "2", "--json", "json.sandbox")

        assertEquals(0, result.exitCode, result.output)
        val response = ProtocolJson.decodeResponse(result.stdout) as MakeResponse
        assertEquals(MakeOutcome.SUCCESS, response.outcome, result.output)
        assertTrue(response.moduleCount >= 2, "expected the language to be made too, got ${response.moduleCount}")
    }

    companion object {
        @JvmField
        @RegisterExtension
        val daemons = SharedDaemons()
    }
}
