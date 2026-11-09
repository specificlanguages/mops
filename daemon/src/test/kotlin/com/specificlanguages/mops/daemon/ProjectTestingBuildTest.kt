package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MakeOutcome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** The build of a test run, which the daemon makes before it starts the test worker. */
class ProjectTestingBuildTest {
    @Test
    fun `a cancelled build stops the run before the test worker starts`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val workspace = TestDaemonWorkspace()
            val cancellation = Files.createTempDirectory("mops-test-cancel").resolve("cancel")
            Files.writeString(cancellation, "cancel")
            val access = workspace.mpsAccess(project)

            val report = workspace.projectTesting(access)
                .run(listOf("84cf8fb2-5a00-4f48-8747-6b797fc155f2(json.sandbox)"), build = true, timeoutMillis = 0, cancellationPath = cancellation.toString())

            assertEquals("CANCELLED", report.outcome, report.toString())
            assertFalse(report.complete)
            assertEquals("BUILD", report.phase)
            assertNotEquals(MakeOutcome.SUCCESS, report.build?.outcome, report.toString())
            assertFalse(Path.of(report.reportPath).resolveSibling("worker.log").exists(), "the test worker must not start")
        }
    }
}
