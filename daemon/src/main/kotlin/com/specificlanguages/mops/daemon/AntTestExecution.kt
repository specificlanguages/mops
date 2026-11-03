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
    private val report: TestReportStore,
    private val limits: TestRunLimits,
) {
    fun run(project: Path, selection: AntTestSelection) {
        report.beginTiming("antStartupAndDiscovery")
        val javaHome = Path.of(System.getProperty("java.home"))
        val adapters = directory.resolve("adapters").createDirectories()
        for (name in listOf("boot", "legacy", "current")) {
            requireNotNull(javaClass.getResourceAsStream("/testing/ant/$name.jar")).use { input ->
                Files.copy(input, adapters.resolve("$name.jar"))
            }
        }
        val boot = adapters.resolve("boot.jar")
        val events = directory.resolve("events").createDirectories()
        val properties = Properties().also { properties -> Files.newInputStream(mps.resolve("build.properties")).use { properties.load(it) } }
        val version = properties.getProperty("mpsBootstrapCore.version")
        // Ant expands ${...} in attribute values; "$$" stands for a literal "$".
        fun attribute(value: Any) = value.toString().replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("$", "$$")
        val config = directory.resolve("test-config").createDirectories()
        // The 2025.1 SVG parser is on Ant's application classpath, but its language is in the Images plugin loader.
        if (version.startsWith("2025.1"))
            config.resolve("disabled_plugins.txt").writeText("com.intellij.platform.images\n")
        val jnaArch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "aarch64" else "amd64"
        val pluginPath = Files.list(mps.resolve("plugins")).use { paths ->
            paths.filter { it.isDirectory() || it.extension == "jar" }.sorted().toList().joinToString(File.pathSeparator)
        }
        val jvmArgs = listOf(
            "-ea", "-Xmx2048m", "-Dplugin.path=$pluginPath", "-Djava.awt.headless=true", "-Didea.config.path=$config",
            "-Didea.system.path=${directory.resolve("test-system")}", "-Didea.log.path=${directory.resolve("test-log")}",
            "-Djna.boot.library.path=${mps.resolve("lib/jna/$jnaArch")}",
            "-Dmops.test.modules=${selection.testModuleIds.joinToString(",")}", "-Dmops.test.adapters=$adapters", "-Dmops.test.events=$events",
        ) + listOfNotNull(selection.model?.let { "-Dmops.test.model=$it" }, selection.node?.let { "-Dmops.test.node=$it" }) +
            if (version.startsWith("2024.1")) listOf("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader") else emptyList()
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
        val antClasspath = listOf(mps.resolve("lib/ant/lib/ant.jar"), mps.resolve("lib/ant/lib/ant-launcher.jar")).joinToString(File.pathSeparator)
        val ant = ProcessBuilder(javaHome.resolve("bin/java").toString(), "-cp", antClasspath, "org.apache.tools.ant.Main", "-f", antFile.toString())
            .redirectErrorStream(true).redirectOutput(directory.resolve("ant.log").toFile()).start()
        var next = 0
        var finished = false
        fun collect() {
            while (true) {
                val path = events.resolve("%08d.properties".format(next))
                if (!path.exists()) break
                val event = Properties().also { properties -> Files.newInputStream(path).use { properties.load(it) } }
                next++
                when {
                    event.containsKey("finished") -> {
                        finished = true
                        report.beginTiming("antShutdown")
                    }
                    event.containsKey("discovered") -> {
                        report.event(mapOf("discovered" to event.getProperty("discovered").toInt()))
                        report.beginTiming("execution")
                        if (event.getProperty("discovered").toInt() > 0) {
                            report.phase("EXECUTION")
                            limits.executionStarted()
                        }
                    }
                    else -> report.event(event.stringPropertyNames().associateWith { event.getProperty(it) })
                }
            }
        }
        try {
            while (!ant.waitFor(50, TimeUnit.MILLISECONDS)) collect()
            collect()
        } finally {
            if (ant.isAlive) destroyProcessTree(ant.toHandle())
        }
        val snapshot = report.snapshot()
        val outcome = when {
            finished && snapshot.discovered == 0 -> "DISCOVERY_FAILED"
            finished && snapshot.results.any { it.status == "FAILED" } -> "TEST_FAILED"
            finished && ant.exitValue() != 0 -> "WORKER_FAILED"
            finished -> "SUCCESS"
            snapshot.phase == "EXECUTION" -> "WORKER_FAILED"
            else -> "DISCOVERY_FAILED"
        }
        val runDiagnostic = if (finished && snapshot.discovered == 0) "No tests discovered for selection: ${selection.description}" else if (!finished || ant.exitValue() != 0 && snapshot.results.none { it.status == "FAILED" }) "Ant test worker exited ${ant.exitValue()}; see ${directory.resolve("ant.log")}\n${directory.resolve("ant.log").readText().takeLast(16000)}" else null
        val classLoading = if (outcome == "SUCCESS") null else classLoadingDiagnostic(directory.resolve("test-log/idea.log"))
        report.finish(outcome, finished || snapshot.phase != "EXECUTION", runDiagnostic, classLoading)
    }

    /**
     * Summarizes the worker's warnings about modules it marked invalid for class loading, which otherwise surface only as
     * a ClassNotFoundException for the test classes of a dependent module. Null when the worker logged none.
     */
    private fun classLoadingDiagnostic(ideaLog: Path): String? {
        if (!ideaLog.exists()) return null
        val warnings = ideaLog.useLines { lines -> lines.mapNotNull { CLASS_LOADING_WARNING.find(it)?.groupValues?.get(1) }.toList() }
        if (warnings.isEmpty()) return null
        val shown = warnings.take(MAX_CLASS_LOADING_WARNINGS)
        return (listOf("The test worker could not load classes of some modules; see $ideaLog") + shown.map { "  $it" } +
            listOfNotNull((warnings.size - shown.size).takeIf { it > 0 }?.let { "  ... $it more" })).joinToString("\n")
    }

    private companion object {
        val CLASS_LOADING_WARNING = Regex("""\bWARN - #?jetbrains\.mps\.classloading\.ModulesWatcher - (.*)""")
        const val MAX_CLASS_LOADING_WARNINGS = 20
    }
}
