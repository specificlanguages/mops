package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/** Creates a solution with `mops create solution`, adds a test to it, and makes and tests it in a worker process. */
@ResourceLock("system-streams")
class CreatedSolutionIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `a solution created with the java-tests preset makes and runs its tests`() {
        val project = copyTestProject("base-language-sandbox", tempDir.resolve("project"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            assertEquals(0, cli("create", "solution", "created.tests", "--usage-preset", "java-tests").exitCode)
            assertEquals(0, cli("create", "model", "created.tests.main", "--module", "created.tests").exitCode)
            val script = tempDir.resolve("content.groovy")
            script.writeText("""
                project.command {
                  def module = mops.lookup.requireModule('created.tests')
                  def descriptor = module.moduleDescriptor
                  ['49808fad-9d41-4b96-83fa-9231640f6b2b(JUnit)', '6354ebe7-c22a-4a0f-ac54-50b52ab9b065(JDK)'].each {
                    descriptor.dependencies.add(new jetbrains.mps.project.structure.modules.Dependency(
                      org.jetbrains.mps.openapi.persistence.PersistenceFacade.instance.createModuleReference(it), false))
                  }
                  module.setModuleDescriptor(descriptor)
                  module.save()
                  mops.parsing.java.addJavaClassesFromString(mops.lookup.requireModel('created.tests.main'),
                    'public class CreatedTest extends junit.framework.TestCase { public void testSum() { assertEquals(2, 1 + 1); } }').unresolved
                }
            """.trimIndent())
            val content = cli("code", "run", script.pathString)
            assertEquals(0, content.exitCode, content.output)
            assertEquals("[]", content.stdout.trim(), content.output)

            val make = cli("make", "module", "created.tests", "--json")
            assertEquals(0, make.exitCode, make.output)
            assertEquals(MakeOutcome.SUCCESS, (ProtocolJson.decodeResponse(make.stdout) as MakeResponse).outcome, make.output)
            val test = cli("test", "created.tests", "--no-build", "--json")
            assertEquals(0, test.exitCode, test.output)
            val report = ProtocolJson.testReport.decode(test.stdout)
            assertEquals("SUCCESS", report.outcome, test.output)
            assertEquals(listOf("PASSED"), report.results.filter { it.kind == "TEST" }.map { it.status }, test.output)
        } finally { stopDaemons(project, home) }
    }
}
