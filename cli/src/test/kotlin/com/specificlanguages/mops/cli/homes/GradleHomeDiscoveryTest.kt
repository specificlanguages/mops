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
            layout.buildDirectory = layout.projectDirectory.dir("output")
            mpsDefaults {
                mpsHome = layout.projectDirectory.dir("MPS home")
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            mpsBuilds.create('main', com.specificlanguages.mps.MainBuild) {
                mpsProjectDirectory = layout.projectDirectory.dir("MPS project")
            }
            mpsBuilds.create('other', com.specificlanguages.mps.MainBuild) {
                mpsProjectDirectory = layout.projectDirectory.dir("Other MPS project")
            }
            tasks.register('buildLanguages') {
                doLast { throw new GradleException('Build action must not run') }
            }
        """)
        val mps = root.resolve("MPS home").createDirectory()
        val mpsProject = root.resolve("MPS project").createDirectory()
        val otherMpsProject = root.resolve("Other MPS project").createDirectory()
        val (exit, output) = run(root)
        assertEquals(0, exit, output)
        assertContains(output, "MPS home from mpsDefaults extension (com.specificlanguages.mps 2.x)")
        assertContains(output, "Java home from mpsDefaults extension (com.specificlanguages.mps 2.x)")
        assertContains(output, "Project root: $mpsProject")
        assertContains(output, "Wrapper: ${root.resolve("output/mops/MPS project/mopsw")}")
        assertContains(output, "Wrapper: ${root.resolve("output/mops/Other MPS project/mopsw")}")
        val wrapper = root.resolve("output/mops/MPS project/mopsw")
        assertTrue(wrapper.isExecutable())
        assertContains(wrapper.readText(), "--mps-home='$mps'")
        assertContains(wrapper.readText(), "--java-home='")
        assertContains(wrapper.readText(), "--project-root='$mpsProject'")
        assertContains(wrapper.readText(), "\"\$@\"")
        assertContains(
            root.resolve("output/mops/Other MPS project/mopsw").readText(),
            "--project-root='$otherMpsProject'",
        )
    }

    @Test
    fun `mbeddr settings come from current project and quote paths`() {
        val root = fixture("""
            buildscript {
                repositories {
                    mavenCentral()
                    maven { url = uri('https://artifacts.itemis.cloud/repository/maven-mps') }
                }
                dependencies { classpath 'de.itemis.mps:mps-gradle-plugin:1.30.2.1.649c88d' }
            }
            class LazyString {
                private final Closure<String> source

                LazyString(Closure<String> source) {
                    this.source = source
                }

                String toString() {
                    source.call()
                }
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
                    executable = new LazyString({ rootProject.file("child's Java/bin/java").absolutePath })
                }
            }
        """, "include 'child'")
        root.resolve("child").createDirectory()
        root.resolve("default MPS").createDirectory()
        root.resolve("child's MPS").createDirectory()
        fakeJava(root.resolve("default Java"))
        fakeJava(root.resolve("child's Java"))
        root.resolve("Root MPS/.mps").createDirectories()
        val nested = root.resolve("child/MPS project/nested").createDirectories()
        root.resolve("child/MPS project/.mps").createDirectory()
        val (rootExit, rootOutput) = run(root)
        assertEquals(0, rootExit, rootOutput)
        assertContains(rootOutput, "MPS home from :buildLanguages arguments (mbeddr RunAntScript)")
        assertContains(rootOutput, "Java home from :buildLanguages executable (mbeddr RunAntScript)")
        assertContains(rootOutput, "MPS home: ${root.resolve("default MPS")}")
        assertContains(rootOutput, "Wrapper: ${root.resolve("build/mops/Root MPS/mopsw")}")
        assertContains(rootOutput, "Wrapper: ${root.resolve("child/build/mops/MPS project/mopsw")}")
        assertEquals(2, rootOutput.lineSequence().count { it.startsWith("Wrapper: ") })
        val (exit, output) = runForProject(nested, root.resolve("child/MPS project"))
        assertEquals(0, exit, output)
        assertContains(output, "MPS home from :child:buildLanguages arguments (mbeddr RunAntScript)")
        assertContains(output, "Java home from :child:buildLanguages executable (mbeddr RunAntScript)")
        assertContains(output, "MPS home: ${root.resolve("child's MPS")}")
        val wrapper = root.resolve("child/build/mops/MPS project/mopsw")
        assertContains(output, "Wrapper: $wrapper")
        assertEquals(1, output.lineSequence().count { it.startsWith("Wrapper: ") })
        assertEquals(
            "#!/bin/sh\nexec mops --mps-home='${root}/child'\"'\"'s MPS' " +
                "--java-home='${root}/child'\"'\"'s Java' " +
                "--project-root='${root}/child/MPS project' \"\$@\"\n",
            wrapper.readText(),
        )
    }

    @Test
    fun `output selects the wrapper file relative to the working directory`() {
        val root = fixture("""
            plugins {
                id 'com.specificlanguages.mps' version '2.1.0'
                id 'java-base'
            }
            mpsDefaults {
                mpsHome = layout.projectDirectory.dir('MPS')
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
        """)
        root.resolve("MPS").createDirectory()
        root.resolve(".mps").createDirectory()

        val (exit, output) = run(root, "--output", "custom/mops-for-project")

        assertEquals(0, exit, output)
        val wrapper = root.resolve("custom/mops-for-project")
        assertContains(output, "Wrapper: $wrapper")
        assertTrue(wrapper.isExecutable())
    }

    @Test
    fun `unprepared mbeddr homes produce a wrapper with warnings`() {
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
        root.resolve("MPS project/.mps").createDirectories()
        val wrapper = root.resolve("custom/mopsw")
        val (exit, output) = run(root, "--output", wrapper.toString())
        assertEquals(0, exit, output)
        assertContains(output, "MPS home: ${root.resolve("missing MPS")}")
        assertContains(output, "Warning: MPS home is missing: ${root.resolve("missing MPS")}")
        assertContains(output, "Warning: Java home is missing or has no usable bin/java: ${root.resolve("missing Java")}")
        assertContains(output, "Wrapper: $wrapper")
        assertContains(wrapper.readText(), "--mps-home='${root.resolve("missing MPS")}'")
        assertContains(wrapper.readText(), "--java-home='${root.resolve("missing Java")}'")
    }

    @Test
    fun `mbeddr resolveMps copy destination supplies the MPS home fallback`() {
        val root = fixture("""
            buildscript {
                repositories {
                    mavenCentral()
                    maven { url = uri('https://artifacts.itemis.cloud/repository/maven-mps') }
                }
                dependencies { classpath 'de.itemis.mps:mps-gradle-plugin:1.30.2.1.649c88d' }
            }
            def resolveMps = tasks.register('resolveMps', Sync) {
                into(layout.buildDirectory.dir('mps'))
                doFirst { throw new GradleException('Copy action must not run') }
            }
            tasks.register('buildLanguages', de.itemis.mps.gradle.BuildLanguages) {
                dependsOn(resolveMps)
                script = 'does-not-exist.xml'
                doFirst { throw new GradleException('Build action must not run') }
            }
        """)
        root.resolve("MPS project/.mps").createDirectories()
        val wrapper = root.resolve("custom/mopsw")

        val (exit, output) = run(root, "--output", wrapper.toString())

        assertEquals(0, exit, output)
        assertContains(output, "MPS home from :resolveMps destination")
        assertContains(output, "Java home from Gradle default Java")
        assertContains(output, "MPS home: ${root.resolve("build/mps")}")
        assertContains(wrapper.readText(), "--mps-home='${root.resolve("build/mps")}'")
    }

    @Test
    fun `downloadJbr supplies Java independently of late RunAnt defaults`() {
        val root = fixture("""
            buildscript {
                repositories {
                    mavenCentral()
                    maven { url = uri('https://artifacts.itemis.cloud/repository/maven-mps') }
                }
                dependencies { classpath 'de.itemis.mps:mps-gradle-plugin:1.30.2.1.649c88d' }
            }
            def resolveMps = tasks.register('resolveMps', Sync) {
                into(layout.buildDirectory.dir('mps'))
                doFirst { throw new GradleException('Copy action must not run') }
            }
            def downloadJbr = tasks.register('downloadJbr') {
                ext.javaExecutable = layout.buildDirectory.file('jbr/Contents/Home/bin/java').get().asFile
                doLast { throw new GradleException('Download action must not run') }
            }
            def configureJava = tasks.register('configureJava') {
                dependsOn(downloadJbr)
                doLast {
                    project.ext['itemis.mps.gradle.ant.defaultJavaExecutable'] = downloadJbr.get().javaExecutable
                }
            }
            tasks.register('buildLanguages', de.itemis.mps.gradle.BuildLanguages) {
                dependsOn(resolveMps, configureJava)
                script = 'does-not-exist.xml'
                doFirst { throw new GradleException('Build action must not run') }
            }
        """)
        root.resolve("MPS project/.mps").createDirectories()
        val wrapper = root.resolve("custom/mopsw")

        val (exit, output) = run(root, "--output", wrapper.toString())

        assertEquals(0, exit, output)
        assertContains(output, "MPS home from :resolveMps destination")
        assertContains(output, "Java home from :downloadJbr task")
        assertContains(output, "Java home: ${root.resolve("build/jbr/Contents/Home")}")
        assertContains(wrapper.readText(), "--java-home='${root.resolve("build/jbr/Contents/Home")}'")
    }

    @Test
    fun `unknown home keeps discovery partial`() {
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
            }
        """)
        root.resolve("MPS project/.mps").createDirectories()
        val wrapper = root.resolve("custom/mopsw")

        val (exit, output) = run(root, "--output", wrapper.toString())

        assertEquals(1, exit, output)
        assertContains(output, "MPS home: unknown")
        assertContains(output, "Discovery is partial; no wrapper was written.")
        assertFalse(wrapper.exists())
    }

    @Test
    fun `custom convention plugins splitting mpsHome and javaHome across extensions are combined`() {
        val root = fixture("""
            plugins {
                id 'java-base'
            }
            project.extensions.add('mpsSettings',
                [mpsHome: providers.provider { layout.projectDirectory.dir('split MPS') }])
            project.extensions.add('jbrToolchain',
                [javaLauncher: javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(17)
                }])
        """)
        val mps = root.resolve("split MPS").createDirectory()
        root.resolve(".mps").createDirectory()
        val (exit, output) = run(root)
        assertEquals(0, exit, output)
        assertContains(output, "MPS home from mpsSettings extension")
        assertContains(output, "Java home from jbrToolchain extension (com.specificlanguages.jbr-toolchain)")
        assertContains(output, "Project root: $root")
        val wrapper = root.resolve("build/mops/${root.fileName}/mopsw")
        assertContains(output, "Wrapper: $wrapper")
        assertTrue(wrapper.isExecutable())
        assertContains(wrapper.readText(), "--mps-home='$mps'")
        assertContains(wrapper.readText(), "--java-home='")
        assertContains(wrapper.readText(), "--project-root='$root'")
    }

    @Test
    fun `split extensions report a partial result when only one home is discovered`() {
        val root = fixture("""
            plugins {
                id 'java-base'
            }
            project.extensions.add('mpsSettings',
                [mpsHome: providers.provider { layout.projectDirectory.dir('missing MPS') }])
        """)
        val (exit, output) = run(root)
        assertEquals(1, exit, output)
        assertContains(output, "MPS home from mpsSettings extension")
        assertContains(output, "MPS home: ${root.resolve("missing MPS")}")
        assertContains(output, "Java home: unknown")
        assertContains(output, "no wrapper was written")
        assertFalse(root.resolve("build/mopsw").exists())
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

    private fun run(directory: Path, vararg args: String): Pair<Int, String> {
        val output = StringWriter()
        val command = newCommandLine(directory)
        command.out = PrintWriter(output, true)
        command.err = PrintWriter(output, true)
        return command.execute("wrapper", *args) to output.toString()
    }

    private fun runForProject(directory: Path, projectRoot: Path, vararg args: String): Pair<Int, String> {
        val output = StringWriter()
        val command = newCommandLine(directory)
        command.out = PrintWriter(output, true)
        command.err = PrintWriter(output, true)
        return command.execute("--project-root", projectRoot.toString(), "wrapper", *args) to output.toString()
    }
}
