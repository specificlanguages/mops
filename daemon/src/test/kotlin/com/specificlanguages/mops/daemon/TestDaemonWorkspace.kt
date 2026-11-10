package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.DaemonWorkspace
import jetbrains.mps.core.platform.Platform
import jetbrains.mps.project.MPSProject
import java.nio.file.Files
import java.nio.file.Path

/**
 * A temporary daemon workspace for tests that construct a [JetBrainsMpsAccess] or [ProjectTesting] directly. The test
 * worker's system directory is the workspace's `test-worker-system` directory, deleted after a run that does not end
 * cleanly, the same as in a daemon started without an explicit worker system directory.
 */
class TestDaemonWorkspace {
    private val path: Path = Files.createTempDirectory("mops-test-workspace")
    private val testWorkerSystem =
        TestWorkerSystemDirectory(DaemonWorkspace(path).testWorkerSystemDir(), deleteAfterUncleanRun = true)

    /** A [JetBrainsMpsAccess] over [project] that uses this workspace and the production collaborators. */
    fun mpsAccess(project: MPSProject, platform: Platform = SharedMpsEnvironment.platform): JetBrainsMpsAccess =
        mpsAccess { workspace, testWorkerSystem ->
            JetBrainsMpsAccess(project, DaemonLogger(), workspace, testWorkerSystem, platform)
        }

    /**
     * The [JetBrainsMpsAccess] that [create] builds from this workspace's directory and test worker system directory,
     * for tests that pass their own collaborators.
     */
    fun mpsAccess(
        create: (workspace: Path, testWorkerSystem: TestWorkerSystemDirectory) -> JetBrainsMpsAccess,
    ): JetBrainsMpsAccess = create(path, testWorkerSystem)

    /** A [ProjectTesting] for [access] that uses this workspace. */
    fun projectTesting(access: JetBrainsMpsAccess): ProjectTesting = ProjectTesting(access, path, testWorkerSystem)
}
