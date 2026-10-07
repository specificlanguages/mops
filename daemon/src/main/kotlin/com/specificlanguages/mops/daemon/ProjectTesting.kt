package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProjectTesting(private val access: JetBrainsMpsAccess, private val workspace: Path) {
    fun run(target: List<String>, build: Boolean, deadlineMillis: Long, cancellationPath: String?): TestRunReport {
        require(!access.project.modelAccess.canRead()) { "testing.run must be called outside a model-access block" }
        val directory = workspace.resolve("test-runs").resolve(UUID.randomUUID().toString())
        Files.createDirectories(directory)
        val reportPath = directory.resolve("report.json")
        val report = TestReportStore(reportPath)
        report.update { it }
        if (deadlineMillis != 0L && System.currentTimeMillis() >= deadlineMillis) {
            report.finish("TIMED_OUT", false)
            return report.snapshot()
        }
        access.extra { saveProject() }
        report.phase("DISCOVERY")
        val selectionPath = directory.resolve("selection.properties")
        try {
            AntTestSelection.resolve(access, target).save(selectionPath)
        } catch (failure: Exception) {
            report.finish("DISCOVERY_FAILED", true, failure.stackTraceToString())
            return report.snapshot()
        }
        val requestPath = directory.resolve("request.json")
        Files.writeString(requestPath, ProtocolJson.encodeRequest(TestRunRequest("", target, build, deadlineMillis, cancellationPath)))
        val classpathFile = directory.resolve("classpath.args")
        val classpath = System.getProperty("java.class.path").replace("\\", "\\\\").replace("\"", "\\\"")
        Files.writeString(classpathFile, "-cp\n\"$classpath\"\n")
        val args = ManagementFactory.getRuntimeMXBean().inputArguments.filterNot {
            it.startsWith("-Djava.awt.headless=") || it.startsWith("-Didea.config.path=") || it.startsWith("-Didea.system.path=") ||
                it.startsWith("-agentlib:") || it.startsWith("-javaagent:")
        }
        val command = listOf(Path.of(System.getProperty("java.home"), "bin", "java").toString()) + args + listOf(
            "-Djava.awt.headless=true",
            "-Didea.config.path=${directory.resolve("idea-config")}",
            "-Didea.system.path=${directory.resolve("idea-system")}",
            "@$classpathFile",
            "com.specificlanguages.mops.daemon.TestPreparationKt", requestPath.toString(), reportPath.toString(),
            requireNotNull(access.project.project.basePath), jetbrains.mps.util.PathManager.getHomePath(), selectionPath.toString(),
        )
        val workerStarted = System.nanoTime()
        val worker = try {
            ProcessBuilder(command).redirectErrorStream(true).redirectOutput(directory.resolve("worker.log").toFile()).start()
        } catch (failure: Exception) {
            report.finish("WORKER_FAILED", false, failure.toString())
            return report.snapshot()
        }
        var interruption: String? = null
        try {
            while (!worker.waitFor(100, TimeUnit.MILLISECONDS)) {
                interruption = when {
                    cancellationPath != null && Files.exists(Path.of(cancellationPath)) -> "CANCELLED"
                    deadlineMillis != 0L && System.currentTimeMillis() >= deadlineMillis -> "TIMED_OUT"
                    else -> null
                }
                if (interruption != null) break
            }
        } finally {
            if (worker.isAlive) {
                worker.descendants().toList().asReversed().forEach { it.destroyForcibly() }
                worker.destroyForcibly()
                worker.waitFor()
            }
            worker.outputStream.close()
        }
        val saved = TestReportStore(reportPath)
        if (interruption != null) saved.finish(interruption, false)
        else if (saved.snapshot().outcome == "RUNNING")
            saved.finish("WORKER_FAILED", false, "Test worker exited ${worker.exitValue()}; see ${directory.resolve("worker.log")}")
        saved.recordTiming("workerLifetime", (System.nanoTime() - workerStarted) / 1_000_000)
        return saved.snapshot()
    }
}
