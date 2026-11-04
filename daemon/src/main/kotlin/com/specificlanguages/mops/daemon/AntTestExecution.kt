package com.specificlanguages.mops.daemon

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

/** Uses the selected distribution's Ant task to construct and start the test-mode JVM. */
class AntTestExecution(
    private val mps: Path,
    private val directory: Path,
    private val systemDirectory: Path,
    private val report: TestReportStore,
    private val limits: TestRunLimits,
) {
    fun run(project: Path, selection: AntTestSelection) {
        report.beginTiming("antStartupAndDiscovery")
        val adapters = copyAdapters()
        val events = directory.resolve("events").createDirectories()
        val antFile = writeAntFile(project, selection, adapters.resolve("boot.jar"), testJvmArgs(project, selection, adapters, events))
        val collector = WorkerEventCollector(events, report, limits)
        val exitCode = runAnt(antFile, collector)
        AntProcessResult(collector.finished, exitCode, directory).finish(report, selection.description)
    }

    /** Copies the packaged boot and launcher adapter JARs into the run directory and returns their directory. */
    private fun copyAdapters(): Path {
        val adapters = directory.resolve("adapters").createDirectories()
        for (name in listOf("boot", "legacy", "current")) {
            requireNotNull(javaClass.getResourceAsStream("/testing/ant/$name.jar")).use { input ->
                Files.copy(input, adapters.resolve("$name.jar"))
            }
        }
        return adapters
    }

    /** The arguments of the test-mode JVM that Ant starts. Creates the run's test configuration directory. */
    private fun testJvmArgs(project: Path, selection: AntTestSelection, adapters: Path, events: Path): List<String> {
        val properties = Properties().also { properties -> Files.newInputStream(mps.resolve("build.properties")).use { properties.load(it) } }
        val version = properties.getProperty("mpsBootstrapCore.version")
        val config = directory.resolve("test-config").createDirectories()
        // The 2025.1 SVG parser is on Ant's application classpath, but its language is in the Images plugin loader.
        if (version.startsWith("2025.1"))
            config.resolve("disabled_plugins.txt").writeText("com.intellij.platform.images\n")
        val jnaArch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "aarch64" else "amd64"
        val pluginPath = Files.list(mps.resolve("plugins")).use { paths ->
            paths.filter { it.isDirectory() || it.extension == "jar" }.sorted().toList().joinToString(File.pathSeparator)
        }
        return listOf(
            "-ea", "-Xmx2048m", "-Dplugin.path=$pluginPath", "-Djava.awt.headless=true", "-Didea.config.path=$config",
            // MPS 2026.3 deletes the test-mode system directory at startup unless mps.test.keep.system.dir is set.
            "-Didea.system.path=$systemDirectory", "-Dmps.test.keep.system.dir=true", "-Didea.log.path=${directory.resolve("test-log")}",
            "-Djna.boot.library.path=${mps.resolve("lib/jna/$jnaArch")}",
            "-Dmops.test.modules=${selection.testModuleIds.joinToString(",")}",
            "-Dmops.test.sources=${selection.sourceFolders(project).joinToString(File.pathSeparator)}",
            "-Dmops.test.adapters=$adapters", "-Dmops.test.events=$events",
        ) + listOfNotNull(selection.model?.let { "-Dmops.test.model=$it" }, selection.node?.let { "-Dmops.test.node=$it" }) +
            if (version.startsWith("2024.1")) listOf("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader") else emptyList()
    }

    /** Writes the Ant file that runs the `launchtests` task for [selection] and returns its path. */
    private fun writeAntFile(project: Path, selection: AntTestSelection, boot: Path, jvmArgs: List<String>): Path {
        val antFile = directory.resolve("launchtests.xml")
        antFile.writeText("""
            <project default="test" basedir="${attribute(project)}">
              <path id="taskcp"><pathelement location="${attribute(mps.resolve("lib/ant/lib/ant-mps.jar"))}"/><fileset dir="${attribute(mps.resolve("lib"))}" includes="util-8.jar"/></path>
              <taskdef resource="jetbrains/mps/build/ant/antlib.xml" classpathref="taskcp"/>
              <property name="mops.test.boot" location="${attribute(boot)}"/>
              <taskdef name="mops.launchtests" classname="com.specificlanguages.mops.testing.MopsLaunchTestTask">
                <classpath><path refid="taskcp"/><pathelement location="${attribute(boot)}"/></classpath>
              </taskdef>
              <target name="test">
                <mops.launchtests worker="com.specificlanguages.mops.testing.AntTestWorker" autoPluginDiscovery="true" mpshome="${attribute(mps)}" haltonfailure="true" reports="${attribute(directory.resolve("xml-reports"))}">
                  <jvmargs>${jvmArgs.joinToString("") { "<arg value=\"${attribute(it)}\"/>" }}</jvmargs>
                  ${selection.macros.entries.joinToString("") { "<macro name=\"${attribute(it.key)}\" path=\"${attribute(it.value)}\"/>" }}
                  <project path="${attribute(project)}"/>
                  <repository>${selection.repositoryModules.joinToString("") { "<module file=\"${attribute(it)}\"/>" } +
                      selection.repositoryFolders.joinToString("") { "<modules dir=\"${attribute(it)}\"/>" }}</repository>
                  <testmodules>${selection.testModules.joinToString("") { "<fileset dir=\"${attribute(it.parent)}\" includes=\"${attribute(it.fileName)}\"/>" }}</testmodules>
                </mops.launchtests>
              </target>
            </project>
        """.trimIndent())
        return antFile
    }

    /** Runs Ant on [antFile], recording the worker's events until Ant exits, and returns the Ant exit code. */
    private fun runAnt(antFile: Path, collector: WorkerEventCollector): Int {
        val java = Path.of(System.getProperty("java.home")).resolve("bin/java").toString()
        val antClasspath = listOf(mps.resolve("lib/ant/lib/ant.jar"), mps.resolve("lib/ant/lib/ant-launcher.jar")).joinToString(File.pathSeparator)
        val ant = ProcessBuilder(java, "-cp", antClasspath, "org.apache.tools.ant.Main", "-f", antFile.toString())
            .redirectErrorStream(true).redirectOutput(directory.resolve("ant.log").toFile()).start()
        try {
            while (!ant.waitFor(50, TimeUnit.MILLISECONDS)) collector.collect()
            collector.collect()
        } finally {
            if (ant.isAlive) destroyProcessTree(ant.toHandle())
        }
        return ant.exitValue()
    }

    private companion object {
        // Ant expands ${...} in attribute values; "$$" stands for a literal "$".
        fun attribute(value: Any) = value.toString().replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("$", "$$")
    }
}
