package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.get.GetModuleCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.GetModuleResponse
import com.specificlanguages.mops.protocol.ModuleLocationsJson
import com.specificlanguages.mops.protocol.ProtocolJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class GetModuleCommandTest {
    @Test
    fun `get module prints one key-value row per reported location of a source module`() {
        val client = mock<DaemonClient>()
        whenever(client.getModule(SOURCE_MODULE)).thenReturn(
            GetModuleResponse(
                ModuleLocationsJson(
                    kind = "language",
                    name = SOURCE_MODULE,
                    reference = SOURCE_REFERENCE,
                    packaged = false,
                    descriptor = "/work/mps-json/languages/com.specificlanguages.json/com.specificlanguages.json.mpl",
                    sourceDirectory = "/work/mps-json/languages/com.specificlanguages.json",
                    classesGen = "/work/mps-json/languages/com.specificlanguages.json/classes_gen",
                    classPath = listOf("/work/mps-json/languages/com.specificlanguages.json/classes_gen"),
                    modelRoots = listOf("/work/mps-json/languages/com.specificlanguages.json"),
                ),
            ),
        )

        val stdout = tapSystemOut { assertEquals(0, run(client, SOURCE_MODULE)) }

        verify(client).getModule(SOURCE_MODULE)
        assertEquals(
            "kind\tlanguage" + nl +
                "name\tcom.specificlanguages.json" + nl +
                "reference\t$SOURCE_REFERENCE" + nl +
                "packaged\tfalse" + nl +
                "descriptor\t/work/mps-json/languages/com.specificlanguages.json/com.specificlanguages.json.mpl" + nl +
                "sourceDirectory\t/work/mps-json/languages/com.specificlanguages.json" + nl +
                "classesGen\t/work/mps-json/languages/com.specificlanguages.json/classes_gen" + nl +
                "classPath\t/work/mps-json/languages/com.specificlanguages.json/classes_gen" + nl +
                "modelRoots\t/work/mps-json/languages/com.specificlanguages.json" + nl,
            stdout,
        )
    }

    @Test
    fun `get module prints the jar-side rows of a packaged module and repeats list rows`() {
        val client = mock<DaemonClient>()
        whenever(client.getModule(PACKAGED_MODULE)).thenReturn(
            GetModuleResponse(
                ModuleLocationsJson(
                    kind = "language",
                    name = PACKAGED_MODULE,
                    reference = PACKAGED_REFERENCE,
                    packaged = true,
                    descriptor = "/opt/mps/languages/languageDesign/jetbrains.mps.lang.core.jar!/META-INF/module.xml",
                    jar = "/opt/mps/languages/languageDesign/jetbrains.mps.lang.core.jar",
                    sourceDescriptor = "/opt/mps/languages/languageDesign/jetbrains.mps.lang.core-src.jar!/module/jetbrains.mps.lang.core.mpl",
                    sourcesJar = "/opt/mps/languages/languageDesign/jetbrains.mps.lang.core-src.jar",
                    classPath = listOf("/opt/mps/languages/languageDesign/jetbrains.mps.lang.core.jar"),
                    modelRoots = listOf(
                        "/opt/mps/languages/languageDesign/jetbrains.mps.lang.core-src.jar!/module",
                        "/opt/mps/languages/languageDesign/jetbrains.mps.lang.core-src.jar!/module/languageModels",
                    ),
                ),
            ),
        )

        val stdout = tapSystemOut { assertEquals(0, run(client, PACKAGED_MODULE)) }

        assertEquals(
            "kind\tlanguage" + nl +
                "name\tjetbrains.mps.lang.core" + nl +
                "reference\t$PACKAGED_REFERENCE" + nl +
                "packaged\ttrue" + nl +
                "descriptor\t/opt/mps/languages/languageDesign/jetbrains.mps.lang.core.jar!/META-INF/module.xml" + nl +
                "jar\t/opt/mps/languages/languageDesign/jetbrains.mps.lang.core.jar" + nl +
                "sourceDescriptor\t/opt/mps/languages/languageDesign/jetbrains.mps.lang.core-src.jar!/module/jetbrains.mps.lang.core.mpl" + nl +
                "sourcesJar\t/opt/mps/languages/languageDesign/jetbrains.mps.lang.core-src.jar" + nl +
                "classPath\t/opt/mps/languages/languageDesign/jetbrains.mps.lang.core.jar" + nl +
                "modelRoots\t/opt/mps/languages/languageDesign/jetbrains.mps.lang.core-src.jar!/module" + nl +
                "modelRoots\t/opt/mps/languages/languageDesign/jetbrains.mps.lang.core-src.jar!/module/languageModels" + nl,
            stdout,
        )
    }

    @Test
    fun `get module emits the daemon response as json when requested`() {
        val client = mock<DaemonClient>()
        val response = GetModuleResponse(
            ModuleLocationsJson(kind = "solution", name = "json.sandbox", reference = SANDBOX_REFERENCE, packaged = false),
        )
        whenever(client.getModule("json.sandbox")).thenReturn(response)

        val stdout = tapSystemOut { assertEquals(0, run(client, "--json", "json.sandbox")) }

        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    @Test
    fun `get module reports the daemon's ambiguity listing and exits nonzero`() {
        val client = mock<DaemonClient>()
        whenever(client.getModule("json.sandbox")).thenThrow(
            IllegalStateException(
                "ambiguous module: json.sandbox; candidates: $SANDBOX_REFERENCE, 11111111-2222-4333-8444-555555555555(json.sandbox)",
            ),
        )

        var exitCode = Int.MIN_VALUE
        val stderr = tapSystemErr { exitCode = run(client, "json.sandbox") }

        assertEquals(1, exitCode)
        assertContains(stderr, "ambiguous module: json.sandbox")
        assertContains(stderr, SANDBOX_REFERENCE)
        assertContains(stderr, "11111111-2222-4333-8444-555555555555(json.sandbox)")
    }

    @Test
    fun `get module requires a module argument`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr { exitCode = newCommandLine().execute("get", "module") }

        assertEquals(2, exitCode)
        assertContains(stderr, "Missing required parameter")
        assertContains(stderr, "MODULE")
    }

    private fun run(client: DaemonClient, vararg args: String): Int =
        CommandLine(GetModuleCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .execute(*args)

    private companion object {
        const val SOURCE_MODULE = "com.specificlanguages.json"
        const val SOURCE_REFERENCE = "f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)"
        const val SANDBOX_REFERENCE = "84cf8fb2-5a00-4f48-8747-6b797fc155f2(json.sandbox)"
        const val PACKAGED_MODULE = "jetbrains.mps.lang.core"
        const val PACKAGED_REFERENCE = "ceab5195-25ea-4f22-9b92-103b95ca8c0c(jetbrains.mps.lang.core)"
        val nl: String = System.lineSeparator()
    }
}
