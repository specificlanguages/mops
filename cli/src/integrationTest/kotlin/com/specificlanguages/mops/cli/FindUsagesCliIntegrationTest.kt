package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.GsonCodec
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ResourceLock("system-streams")
class FindUsagesCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `finds reference usages of a node reference through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runFindUsages(project, daemonHome, "--json", IJSON_VALUE_REFERENCE)

            assertEquals(0, result.exitCode, result.output)
            val response = usagesJson(result)
            assertTrue(response.usages.isNotEmpty(), result.output)
            assertTrue(
                response.usages.any { it.role == "intfc" },
                "expected an implements usage, got: ${response.usages}",
            )
            assertTrue(
                response.usages.all {
                    it.owner.reference.startsWith("r:") && it.owner.concept.isNotBlank()
                },
                "every usage owner should be a real node, got: ${response.usages}",
            )
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `finds reference usages of a model target and node id through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runFindUsages(
                project,
                daemonHome,
                "--json",
                "com.specificlanguages.json.structure",
                "2110045694544566909",
            )

            assertEquals(0, result.exitCode, result.output)
            val response = usagesJson(result)
            assertTrue(response.usages.isNotEmpty(), result.output)
            assertTrue(
                response.usages.any { it.role == "intfc" },
                "expected an implements usage, got: ${response.usages}",
            )
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun runFindUsages(project: Path, daemonHome: Path, vararg args: String): CliResult =
        runCommandLine(
            project,
            "--daemon-home",
            daemonHome.pathString,
            *javaAndMpsHomeArgs(),
            "find",
            "usages",
            *args,
        )

    private fun usagesJson(result: CliResult): FindUsagesResponse =
        GsonCodec.fromJson(result.stdout, FindUsagesResponse::class.java)

    private companion object {
        const val IJSON_VALUE_REFERENCE =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566909"
    }
}
