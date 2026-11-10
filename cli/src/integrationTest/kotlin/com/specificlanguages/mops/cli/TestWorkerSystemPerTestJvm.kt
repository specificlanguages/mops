package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.FullClasspathDaemonLauncher.Companion.TEST_WORKER_SYSTEM_PROPERTY
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import java.nio.file.Path

/**
 * Points the daemons that the CLI starts in this test JVM at one shared test worker system directory, so that each
 * test does not index the MPS distribution again. Gradle runs test JVMs side by side, so each one gets its own
 * directory under `test.workerSystemRoot`, named by Gradle's `org.gradle.test.worker` id. The tests of one JVM run one
 * after another, so no two test runs use the directory at the same time.
 */
class TestWorkerSystemPerTestJvm : LauncherSessionListener {
    override fun launcherSessionOpened(session: LauncherSession) {
        val root = System.getProperty("test.workerSystemRoot") ?: return
        val testJvm = System.getProperty("org.gradle.test.worker") ?: "0"
        System.setProperty(TEST_WORKER_SYSTEM_PROPERTY, Path.of(root, testJvm).toString())
    }
}

/** The JVM option that gives a CLI started in its own process the test worker system directory of this test JVM. */
fun testWorkerSystemJvmArgs(): List<String> =
    System.getProperty(TEST_WORKER_SYSTEM_PROPERTY)?.let { listOf("-D$TEST_WORKER_SYSTEM_PROPERTY=$it") }.orEmpty()
