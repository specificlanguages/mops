package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda
import com.specificlanguages.mops.daemoncomms.FullClasspathDaemonLauncher
import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecordStore
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission.*
import java.time.Duration
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FullClasspathDaemonLauncherTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    @ResourceLock("system-properties")
    fun `startDaemon honors configured daemon classpath property`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "fake Java script is POSIX-only")

        val project = tempDir.mpsProject()
        val mpsHome = tempDir.mpsHome()
        val fakeJava = fakeJavaHome("property-java")

        val configuredClasspath = listOf(
            tempDir.resolve("configured-daemon-a.jar"),
            tempDir.resolve("configured-daemon-b.jar"),
        ).joinToString(File.pathSeparator) { it.pathString }

        val exception = assertFailsWith<IllegalStateException> {
            SystemLambda.restoreSystemProperties {
                System.setProperty("mops.daemon.classpath", configuredClasspath)
                FullClasspathDaemonLauncher(
                    records = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home")),
                    timeout = Duration.ofMillis(500),
                ).startDaemon(
                    context = DaemonContext.fromLivePaths(
                        projectPath = project,
                        mpsHome = mpsHome,
                        javaHome = fakeJava.home,
                    ),
                )
            }
        }

        assertContains(exception.message!!, "daemon exited before writing its project record")
        assertEquals(configuredClasspath, launchedClasspath(fakeJava.argsFile))
    }

    @Test
    @ResourceLock("system-properties")
    fun `startDaemon reads daemon classpath from installed distribution file`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "fake Java script is POSIX-only")

        val project = tempDir.mpsProject()
        val mpsHome = tempDir.mpsHome()
        val fakeJava = fakeJavaHome("distribution-java")
        val applicationHome = tempDir.resolve("mops-app").createDirectories()
        applicationHome.resolve("lib").createDirectories()
        applicationHome.resolve("lib/mops-daemon.classpath").writeText(
            """
            # daemon runtime from distribution
            lib/daemon.jar
            lib/project-loader.jar
            """.trimIndent(),
        )
        val expectedClasspath = listOf(
            applicationHome.resolve("lib/daemon.jar"),
            applicationHome.resolve("lib/project-loader.jar"),
        ).joinToString(File.pathSeparator) { it.normalize().pathString }

        val exception = assertFailsWith<IllegalStateException> {
            SystemLambda.restoreSystemProperties {
                System.clearProperty("mops.daemon.classpath")
                FullClasspathDaemonLauncher(
                    records = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home")),
                    timeout = Duration.ofMillis(500),
                    applicationHome = applicationHome,
                ).startDaemon(
                    context = DaemonContext.fromLivePaths(
                        projectPath = project,
                        mpsHome = mpsHome,
                        javaHome = fakeJava.home,
                    ),
                )
            }
        }

        assertContains(exception.message!!, "daemon exited before writing its project record")
        assertEquals(expectedClasspath, launchedClasspath(fakeJava.argsFile))
    }

    private fun launchedClasspath(argsFile: Path): String {
        assertTrue(argsFile.exists(), "daemon classpath should let launcher invoke the selected Java executable")
        val args = argsFile.readLines()
        val classpathFlag = args.indexOf("-cp")
        assertTrue(classpathFlag >= 0, "daemon process should receive a classpath argument: $args")
        return args[classpathFlag + 1]
    }

    private fun fakeJavaHome(name: String): FakeJavaHome {
        val javaHome = tempDir.resolve(name).createDirectories()
        val argsFile = tempDir.resolve("$name-args.txt")
        val fakeJava = javaHome.resolve("bin").createDirectories().resolve("java")
        fakeJava.writeText(
            """
            #!/bin/sh
            printf '%s\n' "$@" > ${shellQuote(argsFile)}
            exit 42
            """.trimIndent(),
        )
        Files.setPosixFilePermissions(fakeJava, setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
        return FakeJavaHome(javaHome, argsFile)
    }

    private fun shellQuote(path: Path): String =
        "'${path.pathString.replace("'", "'\\''")}'"

    private data class FakeJavaHome(val home: Path, val argsFile: Path)
}
