package com.specificlanguages.mops.cli

import org.junit.jupiter.api.Tag
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
import java.util.jar.JarOutputStream
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("smoke")
class FullClasspathDaemonLauncherTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `product descriptor defines classpath membership and order`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val zJar = emptyJar(mpsHome.resolve("lib/z.jar"))
        val aJar = emptyJar(mpsHome.resolve("lib/a.jar"))
        emptyJar(mpsHome.resolve("lib/undeclared.jar"))
        emptyJar(mpsHome.resolve("lib/modules/module.jar"))
        val mpsTool = emptyJar(mpsHome.resolve("lib/mpsant/mps-tool.jar"))
        productInfo(
            mpsHome.resolve("product-info.json"),
            launch("Linux", "amd64", "z.jar", "missing.jar", "a.jar", "mpsant/mps-tool.jar"),
        )

        val classpath = FullClasspathDaemonLauncher.mpsRuntimeClasspath(mpsHome, "Linux", "x86_64")

        assertEquals(listOf(zJar.pathString, aJar.pathString, mpsTool.pathString), classpath)
        assertEquals(1, classpath.count { it == mpsTool.pathString })
        assertFalse(classpath.any { it.endsWith("undeclared.jar") || it.endsWith("module.jar") })
    }

    @Test
    fun `exact host launch entry is preferred`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val linuxJar = emptyJar(mpsHome.resolve("lib/linux.jar"))
        emptyJar(mpsHome.resolve("lib/mac.jar"))
        productInfo(
            mpsHome.resolve("product-info.json"),
            launch("macOS", "aarch64", "mac.jar"),
            launch("Linux", "amd64", "linux.jar"),
        )

        assertEquals(
            listOf(linuxJar.pathString),
            FullClasspathDaemonLauncher.mpsRuntimeClasspath(mpsHome, "Linux", "amd64"),
        )
    }

    @Test
    fun `sole nonmatching launch entry is accepted for generic distributions`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val bootJar = emptyJar(mpsHome.resolve("lib/boot.jar"))
        productInfo(mpsHome.resolve("product-info.json"), launch("Linux", "amd64", "boot.jar"))

        assertEquals(
            listOf(bootJar.pathString),
            FullClasspathDaemonLauncher.mpsRuntimeClasspath(mpsHome, "Mac OS X", "aarch64"),
        )
    }

    @Test
    fun `common boot classpath is accepted when no launch entry matches`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val bootJar = emptyJar(mpsHome.resolve("lib/boot.jar"))
        productInfo(
            mpsHome.resolve("product-info.json"),
            launch("Linux", "amd64", "boot.jar"),
            launch("Windows", "amd64", "boot.jar"),
        )

        assertEquals(
            listOf(bootJar.pathString),
            FullClasspathDaemonLauncher.mpsRuntimeClasspath(mpsHome, "Mac OS X", "arm64"),
        )
    }

    @Test
    fun `ambiguous nonmatching launch entries fail with descriptor path`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val descriptor = mpsHome.resolve("product-info.json")
        productInfo(
            descriptor,
            launch("Linux", "amd64", "linux.jar"),
            launch("Windows", "amd64", "windows.jar"),
        )

        val exception = assertFailsWith<IllegalStateException> {
            FullClasspathDaemonLauncher.mpsRuntimeClasspath(mpsHome, "Mac OS X", "aarch64")
        }

        assertContains(exception.message!!, "Ambiguous launch entries")
        assertContains(exception.message!!, descriptor.pathString)
    }

    @Test
    fun `product descriptor is found below Resources`() {
        val mpsHome = tempDir.resolve("MPS.app/Contents").createDirectories()
        val bootJar = emptyJar(mpsHome.resolve("lib/boot.jar"))
        productInfo(mpsHome.resolve("Resources/product-info.json"), launch("macOS", "aarch64", "boot.jar"))

        assertEquals(
            listOf(bootJar.pathString),
            FullClasspathDaemonLauncher.mpsRuntimeClasspath(mpsHome, "Mac OS X", "arm64"),
        )
    }

    @Test
    fun `missing product descriptor uses legacy classpath`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val aJar = emptyJar(mpsHome.resolve("lib/a.jar"))
        val zJar = emptyJar(mpsHome.resolve("lib/z.jar"))
        val moduleJar = emptyJar(mpsHome.resolve("lib/modules/module.jar"))
        val mpsTool = emptyJar(mpsHome.resolve("lib/mpsant/mps-tool.jar"))

        assertEquals(
            listOf(aJar.pathString, zJar.pathString, moduleJar.pathString, mpsTool.pathString),
            FullClasspathDaemonLauncher.mpsRuntimeClasspath(mpsHome, "Linux", "amd64"),
        )
    }

    @Test
    fun `malformed product descriptor does not use legacy classpath`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val descriptor = mpsHome.resolve("product-info.json")
        descriptor.writeText("""{"launch":[{"os":1,"arch":"amd64","bootClassPathJarNames":[]}]}""")
        emptyJar(mpsHome.resolve("lib/fallback.jar"))

        val exception = assertFailsWith<IllegalStateException> {
            FullClasspathDaemonLauncher.mpsRuntimeClasspath(mpsHome, "Linux", "amd64")
        }

        assertContains(exception.message!!, "Invalid MPS product descriptor")
        assertContains(exception.message!!, descriptor.pathString)
    }

    @Test
    fun `startup timeout defaults to 300 seconds and accepts larger values`() {
        assertEquals(Duration.ofSeconds(300), FullClasspathDaemonLauncher.startupTimeoutFromEnvironment(null))
        assertEquals(Duration.ofSeconds(600), FullClasspathDaemonLauncher.startupTimeoutFromEnvironment("600"))
    }

    @Test
    fun `startup timeout rejects invalid values including millisecond overflow`() {
        for (value in listOf("", "0", "-1", "1.5", "abc", Long.MAX_VALUE.toString(), "99999999999999999999")) {
            val exception = assertFailsWith<IllegalArgumentException>(value) {
                FullClasspathDaemonLauncher.startupTimeoutFromEnvironment(value)
            }
            assertContains(exception.message!!, "MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS")
            assertContains(exception.message!!, "positive whole number of seconds")
        }
    }

    @Test
    @ResourceLock("system-properties")
    fun `startup timeout reports configured duration and terminates the child`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "fake Java script is POSIX-only")
        val project = tempDir.mpsProject()
        val mpsHome = tempDir.mpsHome()
        val fakeJava = fakeJavaHome("slow-java")
        val pidFile = tempDir.resolve("daemon.pid")
        fakeJava.home.resolve("bin/java").writeText(
            """
            #!/bin/sh
            echo $$ > ${shellQuote(pidFile)}
            exec sleep 60
            """.trimIndent(),
        )

        val exception = assertFailsWith<IllegalStateException> {
            SystemLambda.restoreSystemProperties {
                System.setProperty("mops.daemon.classpath", "unused.jar")
                System.setProperty(
                    FullClasspathDaemonLauncher.DAEMON_MPS_PLUGIN_PROPERTY,
                    emptyJar("timeout-plugin.jar").pathString,
                )
                FullClasspathDaemonLauncher(
                    records = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home")),
                    startupTimeout = FullClasspathDaemonLauncher.startupTimeoutFromEnvironment("1"),
                ).startDaemon(DaemonContext.fromLivePaths(project, mpsHome, fakeJava.home))
            }
        }

        assertContains(exception.message!!, "timed out waiting for daemon project record after 1 seconds")
        assertContains(exception.message!!, "MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS")
        assertContains(exception.message!!, "Daemon log:")
        val child = ProcessHandle.of(pidFile.readText().trim().toLong())
        if (child.isPresent) {
            child.get().onExit().get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(!child.get().isAlive, "timed-out daemon should be terminated")
        }
    }

    @Test
    @ResourceLock("system-properties")
    fun `startDaemon honors configured daemon classpath property`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "fake Java script is POSIX-only")

        val project = tempDir.mpsProject()
        val mpsHome = tempDir.mpsHome()
        val mpsTool = mpsHome.resolve("lib/mpsant").createDirectories().resolve("mps-tool.jar")
            .also { JarOutputStream(Files.newOutputStream(it)).use { } }
        val fakeJava = fakeJavaHome("property-java")

        val configuredClasspath = listOf(
            tempDir.resolve("configured-daemon-a.jar"),
            tempDir.resolve("configured-daemon-b.jar"),
        ).joinToString(File.pathSeparator) { it.pathString }

        val exception = assertFailsWith<IllegalStateException> {
            SystemLambda.restoreSystemProperties {
                System.setProperty("mops.daemon.classpath", configuredClasspath)
                System.setProperty(
                    FullClasspathDaemonLauncher.DAEMON_MPS_PLUGIN_PROPERTY,
                    emptyJar("property-plugin.jar").pathString,
                )
                FullClasspathDaemonLauncher(
                    records = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home")),
                    startupTimeout = Duration.ofMillis(500),
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
        assertContains(fakeJava.commandArgsFile.readText(), "-Didea.force.use.core.classloader=false")
        assertEquals(
            listOf(configuredClasspath, mpsTool.pathString).joinToString(File.pathSeparator),
            launchedClasspath(fakeJava.argsFile),
        )
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
        emptyJar(applicationHome.resolve("mps-plugins/mops-daemon-plugin.jar"))
        val expectedClasspath = listOf(
            applicationHome.resolve("lib/daemon.jar"),
            applicationHome.resolve("lib/project-loader.jar"),
        ).joinToString(File.pathSeparator) { it.normalize().pathString }

        val exception = assertFailsWith<IllegalStateException> {
            SystemLambda.restoreSystemProperties {
                System.clearProperty("mops.daemon.classpath")
                System.clearProperty(FullClasspathDaemonLauncher.DAEMON_MPS_PLUGIN_PROPERTY)
                FullClasspathDaemonLauncher(
                    records = DaemonRecordStore.forDaemonHome(tempDir.resolve("daemon-home")),
                    startupTimeout = Duration.ofMillis(500),
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

    @Test
    fun `workspace lock diagnostic names a live foreign lock holder`() {
        val configDir = tempDir.resolve("config").createDirectories()
        configDir.resolve(".lock").writeText("4242\n")

        val message = FullClasspathDaemonLauncher.workspaceLockHolderMessage(
            configDir = configDir,
            ownPid = 100,
            isAlive = { it == 4242L },
        )

        assertContains(message!!, "pid 4242")
        assertContains(message, configDir.pathString)
        assertContains(message, "mops daemon stop")
    }

    @Test
    fun `workspace lock diagnostic is silent for our own pid, a dead holder, or no lock`() {
        val configDir = tempDir.resolve("config").createDirectories()

        // No lock file yet.
        assertEquals(null, FullClasspathDaemonLauncher.workspaceLockHolderMessage(configDir, 100) { true })

        configDir.resolve(".lock").writeText("100")
        // The lock is held by our own daemon pid.
        assertEquals(null, FullClasspathDaemonLauncher.workspaceLockHolderMessage(configDir, 100) { true })

        configDir.resolve(".lock").writeText("999999")
        // The holder is no longer running.
        assertEquals(null, FullClasspathDaemonLauncher.workspaceLockHolderMessage(configDir, 100) { false })
    }

    private fun launchedClasspath(argsFile: Path): String {
        assertTrue(argsFile.exists(), "daemon classpath should let launcher invoke the selected Java executable")
        val classpathArgs = argsFile.readLines()
        assertEquals("-cp", classpathArgs[0])
        return classpathArgs[1].removeSurrounding("\"")
    }

    private fun emptyJar(name: String): Path = emptyJar(tempDir.resolve(name))

    private fun emptyJar(path: Path): Path {
        path.parent.createDirectories()
        JarOutputStream(Files.newOutputStream(path)).use { }
        return path
    }

    private fun productInfo(path: Path, vararg launches: String) {
        path.parent.createDirectories()
        path.writeText("""{"launch":[${launches.joinToString()}]}""")
    }

    private fun launch(os: String, arch: String, vararg jars: String): String =
        """{"os":"$os","arch":"$arch","bootClassPathJarNames":[${jars.joinToString { "\"$it\"" }}]}"""

    private fun fakeJavaHome(name: String): FakeJavaHome {
        val javaHome = tempDir.resolve(name).createDirectories()
        val argsFile = tempDir.resolve("$name-args.txt")
        val commandArgsFile = tempDir.resolve("$name-command-args.txt")
        val fakeJava = javaHome.resolve("bin").createDirectories().resolve("java")
        fakeJava.writeText(
            """
            #!/bin/sh
            printf '%s\n' "$@" > ${shellQuote(commandArgsFile)}
            for argument in "$@"; do
                case "${'$'}argument" in
                    @*) cat "${'$'}{argument#@}" > ${shellQuote(argsFile)} ;;
                esac
            done
            exit 42
            """.trimIndent(),
        )
        Files.setPosixFilePermissions(fakeJava, setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
        return FakeJavaHome(javaHome, argsFile, commandArgsFile)
    }

    private fun shellQuote(path: Path): String =
        "'${path.pathString.replace("'", "'\\''")}'"

    private data class FakeJavaHome(val home: Path, val argsFile: Path, val commandArgsFile: Path)
}
