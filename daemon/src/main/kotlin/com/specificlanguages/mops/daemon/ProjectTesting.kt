package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import java.io.File
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
        val requestPath = directory.resolve("request.json")
        Files.writeString(requestPath, ProtocolJson.encodeRequest(TestRunRequest("", target, build, deadlineMillis, cancellationPath)))
        val classpathFile = directory.resolve("classpath.args")
        // IDEA's SVG parser references this plugin directly during background indexing in MPS 2025.1.
        val images = Path.of(jetbrains.mps.util.PathManager.getHomePath(), "plugins/platform-images/lib/platform-images.jar")
        val entries = listOf(System.getProperty("java.class.path")) + listOf(images).filter(Files::isRegularFile).map(Path::toString)
        val classpath = entries.joinToString(File.pathSeparator).replace("\\", "\\\\").replace("\"", "\\\"")
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
            "com.specificlanguages.mops.daemon.TestWorkerKt", requestPath.toString(), reportPath.toString(),
            requireNotNull(access.project.project.basePath), jetbrains.mps.util.PathManager.getHomePath(),
        )
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
                worker.descendants().forEach { it.destroyForcibly() }
                worker.destroyForcibly()
                worker.waitFor()
            }
            worker.outputStream.close()
        }
        val saved = TestReportStore(reportPath)
        if (interruption != null) saved.finish(interruption, false)
        else if (saved.snapshot().outcome == "RUNNING")
            saved.finish("WORKER_FAILED", false, "Test worker exited ${worker.exitValue()}; see ${directory.resolve("worker.log")}")
        return saved.snapshot()
    }
}
