package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs `make module` through the CLI, launcher, and daemon against a project with a language and its sandbox. */
@ResourceLock("system-streams")
class MakeIntegrationTest {
    @Test
    fun `make module passes its generation options to MPS`() {
        val result = daemons.project("mps-json").run(
            "make", "module", "--threads", "2", "--strict", "--messages", "info", "--json", "json.sandbox",
        )

        assertEquals(0, result.exitCode, result.output)
        val response = ProtocolJson.decodeResponse(result.stdout) as MakeResponse
        assertEquals(MakeOutcome.SUCCESS, response.outcome, result.output)
        assertTrue(response.moduleCount >= 2, "expected the language to be made too, got ${response.moduleCount}")
        assertTrue(
            response.messages.any { it.kind == MakeMessageKind.INFO && it.text.contains("using 2 threads") },
            "expected MPS to report generating with 2 threads, got ${response.messages}",
        )
    }

    @Test
    fun `make module leaves out a solution created with the not-generated preset`() {
        val project = daemons.project("mps-json", copy = "not-generated")
        val created = project.run("create", "solution", "example.analyzed")
        assertEquals(0, created.exitCode, created.output)
        val model = project.run("create", "model", "example.analyzed.main", "--module", "example.analyzed")
        assertEquals(0, model.exitCode, model.output)

        val result = project.run("make", "module", "example.analyzed")

        assertEquals(0, result.exitCode, result.output)
        assertEquals("nothing to generate", result.stdout.trim(), result.output)
        assertContains(result.stderr, "warning\tnot generating the models of example.analyzed")
    }

    companion object {
        @JvmField
        @RegisterExtension
        val daemons = SharedDaemons()
    }
}
