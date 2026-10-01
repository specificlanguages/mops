package com.specificlanguages.mops.daemon

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

/** Uses the selected distribution's Ant task to construct and start the test-mode JVM. */
class AntTestExecution(private val mps: Path, private val directory: Path, private val report: TestReportStore) {
    fun run(project: Path, selection: AntTestSelection) {
        val (modules, moduleIds, libraries, model, node) = selection
        val javaHome = Path.of(System.getProperty("java.home"))
        val sources = directory.resolve("sources").createDirectories()
        val boot = directory.resolve("boot").createDirectories()
        val adapter = directory.resolve("adapter").createDirectories()
        val events = directory.resolve("events").createDirectories()
        val properties = Properties().also { properties -> Files.newInputStream(mps.resolve("build.properties")).use { properties.load(it) } }
        val version = properties.getProperty("mpsBootstrapCore.version")
        val legacy = version.startsWith("2024.1") || version.startsWith("2025.1")
        fun resource(name: String) = requireNotNull(javaClass.getResource("/testing/ant/$name")).readText()
        val worker = sources.resolve("AntTestWorker.java").also { it.writeText(resource("AntTestWorker.java")) }
        val task = sources.resolve("MopsLaunchTestTask.java").also { it.writeText(resource("MopsLaunchTestTask.java")) }
        val launcher = sources.resolve("ModelLauncher.java").also { it.writeText(resource(if (legacy) "legacy.java" else "current.java")) }
        val support = sources.resolve("SelectionSupport.java").also { it.writeText(resource("SelectionSupport.java")) }
        val compileClasspath = listOf(mps.resolve("lib"), mps.resolve("plugins/mps-testing"), mps.resolve("plugins/mps-junit5"))
            .flatMap { root -> Files.walk(root).use { paths -> paths.filter { it.toString().endsWith(".jar") }.toList() } }
            .filterNot { it.name.endsWith("-src.jar") || it.name.endsWith("-sources.jar") }
            .joinToString(File.pathSeparator)
        fun compile(destination: Path, vararg files: Path) {
            val argsFile = sources.resolve("javac.args")
            fun quote(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            argsFile.writeText((listOf("-proc:none", "-cp", compileClasspath, "-d", destination.toString()) + files.map { it.toString() })
                .joinToString("\n", transform = ::quote))
            val compiler = ProcessBuilder(javaHome.resolve("bin/javac").toString(), "@$argsFile")
                .redirectErrorStream(true).redirectOutput(directory.resolve("compile.log").toFile()).start()
            check(compiler.waitFor() == 0) { "Cannot compile Ant test adapter: ${directory.resolve("compile.log").readText()}" }
        }
        compile(boot, worker, task)
        compile(adapter, launcher, support)
        fun xml(value: Any) = value.toString().replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
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
            "-Dmops.test.modules=${moduleIds.joinToString(",")}", "-Dmops.test.adapter=$adapter", "-Dmops.test.events=$events",
        ) + listOfNotNull(model?.let { "-Dmops.test.model=$it" }, node?.let { "-Dmops.test.node=$it" }) +
            if (version.startsWith("2024.1")) listOf("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader") else emptyList()
        val antFile = directory.resolve("launchtests.xml")
        antFile.writeText("""
            <project default="test" basedir="${xml(project)}">
              <path id="taskcp"><pathelement location="${xml(mps.resolve("lib/ant/lib/ant-mps.jar"))}"/><fileset dir="${xml(mps.resolve("lib"))}" includes="util-8.jar"/></path>
              <taskdef resource="jetbrains/mps/build/ant/antlib.xml" classpathref="taskcp"/>
              <property name="mops.test.boot" location="${xml(boot)}"/>
              <taskdef name="mops.launchtests" classname="com.specificlanguages.mops.testing.MopsLaunchTestTask">
                <classpath><path refid="taskcp"/><pathelement location="${xml(boot)}"/></classpath>
              </taskdef>
              <target name="test">
                <mops.launchtests worker="com.specificlanguages.mops.testing.AntTestWorker" autoPluginDiscovery="true" mpshome="${xml(mps)}" haltonfailure="true" reports="${xml(directory.resolve("xml-reports"))}">
                  <jvmargs>${jvmArgs.joinToString("") { "<arg value=\"${xml(it)}\"/>" }}</jvmargs>
                  <project path="${xml(project)}"/>
                  <repository>${libraries.joinToString("") { "<module file=\"${xml(it)}\"/>" }}</repository>
                  <testmodules>${modules.joinToString("") { "<fileset dir=\"${xml(it.parent)}\" includes=\"${xml(it.fileName)}\"/>" }}</testmodules>
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
                    event.containsKey("finished") -> finished = true
                    event.containsKey("discovered") -> {
                        report.event(mapOf("discovered" to event.getProperty("discovered").toInt()))
                        if (event.getProperty("discovered").toInt() > 0) report.phase("EXECUTION")
                    }
                    else -> report.event(event.stringPropertyNames().associateWith { event.getProperty(it) })
                }
            }
        }
        try {
            while (!ant.waitFor(50, TimeUnit.MILLISECONDS)) collect()
            collect()
        } finally {
            if (ant.isAlive) {
                ant.descendants().toList().asReversed().forEach { it.destroyForcibly() }
                ant.destroyForcibly()
            }
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
        report.finish(outcome, finished || snapshot.phase != "EXECUTION",
            if (finished && snapshot.discovered == 0) "No tests discovered for the selection" else if (!finished || ant.exitValue() != 0 && snapshot.results.none { it.status == "FAILED" }) "Ant test worker exited ${ant.exitValue()}; see ${directory.resolve("ant.log")}\n${directory.resolve("ant.log").readText().takeLast(16000)}" else null)
    }
}
