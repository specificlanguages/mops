package com.specificlanguages.mops.daemon

import jetbrains.mps.core.platform.Platform
import jetbrains.mps.project.MPSProject
import java.nio.file.Files
import java.nio.file.Path

/**
 * A temporary daemon workspace for tests that construct a [JetBrainsMpsAccess] or [ProjectTesting] directly. The test
 * system cache is the `test-system-cache` directory inside the workspace.
 */
class TestDaemonWorkspace(val path: Path = Files.createTempDirectory("mops-test-workspace")) {
    val testSystemCache: Path = path.resolve("test-system-cache")

    /** A [JetBrainsMpsAccess] over [project] that uses this workspace and the production collaborators. */
    fun mpsAccess(project: MPSProject, platform: Platform = SharedMpsEnvironment.platform): JetBrainsMpsAccess =
        JetBrainsMpsAccess(project, DaemonLogger(), path, testSystemCache, platform)

    /** A [ProjectTesting] for [access] that uses this workspace. */
    fun projectTesting(access: JetBrainsMpsAccess): ProjectTesting = ProjectTesting(access, path, testSystemCache)
}
