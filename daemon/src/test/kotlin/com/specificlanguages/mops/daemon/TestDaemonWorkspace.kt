package com.specificlanguages.mops.daemon

import jetbrains.mps.core.platform.Platform
import jetbrains.mps.project.MPSProject
import java.nio.file.Files
import java.nio.file.Path

/**
 * A temporary daemon workspace for tests that construct a [JetBrainsMpsAccess] or [ProjectTesting] directly. The test
 * system cache is the `test-system-cache` directory inside the workspace.
 */
class TestDaemonWorkspace {
    private val path: Path = Files.createTempDirectory("mops-test-workspace")
    private val testSystemCache: Path = path.resolve("test-system-cache")

    /** A [JetBrainsMpsAccess] over [project] that uses this workspace and the production collaborators. */
    fun mpsAccess(project: MPSProject, platform: Platform = SharedMpsEnvironment.platform): JetBrainsMpsAccess =
        mpsAccess { workspace, testSystemCache ->
            JetBrainsMpsAccess(project, DaemonLogger(), workspace, testSystemCache, platform)
        }

    /**
     * The [JetBrainsMpsAccess] that [create] builds from this workspace's directory and test system cache, for tests
     * that pass their own collaborators.
     */
    fun mpsAccess(create: (workspace: Path, testSystemCache: Path) -> JetBrainsMpsAccess): JetBrainsMpsAccess =
        create(path, testSystemCache)

    /** A [ProjectTesting] for [access] that uses this workspace. */
    fun projectTesting(access: JetBrainsMpsAccess): ProjectTesting = ProjectTesting(access, path, testSystemCache)
}
