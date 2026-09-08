package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.newCommandLine
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

@Tag("gradle-discovery")
class GradleHomeDiscoveryTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `specific languages providers supply homes without daemon configuration or build actions`() {
        val root = fixture("""
            plugins {
                id 'com.specificlanguages.mps' version '2.1.0'
                id 'java-base'
            }
            mpsDefaults {
                mpsHome = layout.projectDirectory.dir("MPS home")
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            tasks.register('buildLanguages') {
                doLast { throw new GradleException('Build action must not run') }
            }
        """)
        val mps = root.resolve("MPS home").createDirectory()
        val (exit, output) = run(root)
        assertEquals(0, exit, output)
        assertContains(output, "mpsDefaults (com.specificlanguages.mps 2.x)")
        assertContains(output, "--mps-home='$mps'")
        assertContains(output, "--java-home='")
    }

    @Test
    fun `provider failure preserves MPS discovery`() {
        val root = fixture("""
            plugins {
                id 'com.specificlanguages.mps' version '2.1.0'
                id 'java-base'
            }
            mpsDefaults {
                mpsHome = layout.projectDirectory.dir("MPS home")
                javaLauncher = providers.provider { throw new GradleException('Java provider unavailable') }
            }
        """)
        val mps = root.resolve("MPS home").createDirectory()
        val (exit, output) = run(root)
        assertEquals(1, exit, output)
        assertContains(output, "Java provider unavailable")
        assertContains(output, "--mps-home='$mps'")
        assertFalse(output.contains("--java-home='"))
    }

    @Test
    fun `mbeddr defaults and closest task override keep pairs together and quote paths`() {
        val root = fixture("""
            buildscript {
                repositories {
                    mavenCentral()
                    maven { url = uri('https://artifacts.itemis.cloud/repository/maven-mps') }
                }
                dependencies { classpath 'de.itemis.mps:mps-gradle-plugin:1.30.2.1.649c88d' }
            }
            allprojects {
                ext['itemis.mps.gradle.ant.defaultScriptArgs'] =
                    ["-Dmps.home=" + rootProject.file("default MPS")]
                ext['itemis.mps.gradle.ant.defaultJavaExecutable'] =
                    rootProject.file("default Java/bin/java")
                tasks.register('buildLanguages', de.itemis.mps.gradle.BuildLanguages) {
                    script = 'does-not-exist.xml'
                    doFirst { throw new GradleException('Build action must not run') }
                }
            }
            project(':child') {
                tasks.named('buildLanguages') {
                    includeDefaultArgs = false
                    scriptArgs = ["-Dmps_home=" + rootProject.file("child's MPS")]
                    executable = rootProject.file("child's Java/bin/java").absolutePath
                }
            }
        """, "include 'child'")
        root.resolve("child").createDirectory()
        root.resolve("default MPS").createDirectory()
        root.resolve("child's MPS").createDirectory()
        fakeJava(root.resolve("default Java"))
        fakeJava(root.resolve("child's Java"))
        val (rootExit, rootOutput) = run(root)
        assertEquals(0, rootExit, rootOutput)
        assertContains(rootOutput, "Source: :buildLanguages")
        assertContains(rootOutput, "MPS home: ${root.resolve("default MPS")}")
        val nested = root.resolve("child/nested").createDirectories()
        val (exit, output) = run(nested)
        assertEquals(0, exit, output)
        assertContains(output, "Source: :child:buildLanguages")
        assertContains(output, "MPS home: ${root.resolve("child's MPS")}")
        val arguments = output.lineSequence().first { it.startsWith("Command line (POSIX shell): ") }
            .substringAfter("Command line (POSIX shell): ")
        val shell = ProcessBuilder("sh", "-c", "set -- $arguments; printf '%s\\n' \"\$@\"")
            .redirectErrorStream(true).start()
        assertEquals(
            listOf("mops", "--mps-home=${root.resolve("child's MPS")}",
                "--java-home=${root.resolve("child's Java")}"),
            shell.inputStream.bufferedReader().readLines(),
        )
        assertEquals(0, shell.waitFor())
    }

    @Test
    fun `unprepared mbeddr homes report configured paths without reusable arguments`() {
        val root = fixture("""
            buildscript {
                repositories {
                    mavenCentral()
                    maven { url = uri('https://artifacts.itemis.cloud/repository/maven-mps') }
                }
                dependencies { classpath 'de.itemis.mps:mps-gradle-plugin:1.30.2.1.649c88d' }
            }
            tasks.register('buildLanguages', de.itemis.mps.gradle.BuildLanguages) {
                script = 'does-not-exist.xml'
                scriptArgs = ['-Dmps.home=' + project.file('missing MPS')]
                executable = project.file('missing Java/bin/java')
            }
        """)
        val (exit, output) = run(root)
        assertEquals(1, exit, output)
        assertContains(output, "MPS home: ${root.resolve("missing MPS")}")
        assertContains(output, "runtime preparation")
        assertFalse(output.contains("Command line (POSIX shell):"))
    }

    private fun fixture(build: String, settings: String = ""): Path {
        val root = temporary.resolve("project with spaces").createDirectory().toRealPath()
        root.resolve("settings.gradle").writeText("""
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    maven { url = uri('https://artifacts.itemis.cloud/repository/maven-mps') }
                }
            }
            rootProject.name = 'probe-fixture'
            $settings
        """.trimIndent())
        root.resolve("build.gradle").writeText(build.trimIndent())
        val repository = Path.of(System.getProperty("test.repoRoot"))
        root.resolve("gradle/wrapper").createDirectories()
        listOf("gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties").forEach {
            repository.resolve(it).copyTo(root.resolve(it))
        }
        return root
    }

    private fun fakeJava(home: Path) {
        home.resolve("bin").createDirectories()
        home.resolve("bin/java").writeText("#!/bin/sh\nexit 0\n")
        assertTrue(home.resolve("bin/java").toFile().setExecutable(true))
    }

    private fun run(directory: Path): Pair<Int, String> {
        val output = StringWriter()
        val command = newCommandLine(directory)
        command.out = PrintWriter(output, true)
        command.err = PrintWriter(output, true)
        return command.execute("guess-command-line") to output.toString()
    }
}
