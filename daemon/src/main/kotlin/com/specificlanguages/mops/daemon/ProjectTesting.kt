package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.launcher.MpsBuildProperties
import com.specificlanguages.mops.protocol.*
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class ProjectTesting(private val access: JetBrainsMpsAccess, private val workspace: Path, private val testSystemCache: Path) {
    fun run(target: List<String>, build: Boolean, timeoutMillis: Long, cancellationPath: String?): TestRunReport {
        require(!access.project.modelAccess.canRead()) { "testing.run must be called outside a model-access block" }
        val directory = workspace.resolve("test-runs").resolve(UUID.randomUUID().toString())
        Files.createDirectories(directory)
        val reportPath = directory.resolve("report.json")
        val report = TestReportStore(reportPath)
        report.update { it }
        access.extra { saveProject() }
        report.phase("DISCOVERY")
        val selectionPath = directory.resolve("selection.properties")
        try {
            val selection = AntTestSelection.resolve(access.project, target)
            selection.save(selectionPath)
            report.update { it.copy(diagnostics = it.diagnostics + selection.diagnostics) }
        } catch (failure: Exception) {
            report.finish(TestRunConclusion("DISCOVERY_FAILED", complete = true, listOf(failure.stackTraceToString())))
            return report.snapshot()
        }
        val mpsHome = Path.of(jetbrains.mps.util.PathManager.getHomePath())
        val mpsBuild = requireNotNull(MpsBuildProperties.fromMpsHome(mpsHome)?.buildNumber) {
            "MPS build number not found in ${mpsHome.resolve("build.properties")}"
        }
        val systems = PersistentSystemDirectories(testSystemCache, mpsBuild)
        // The preparation and test-worker JVMs run at the same time, so each needs its own system directory. A no-build
        // run never starts IntelliJ in the preparation JVM, so it needs no persistent directory there.
        systems.acquire("worker", "test worker", directory.resolve("test-system")).use { workerSystem ->
            val preparationFallback = directory.resolve("idea-system")
            val preparationSystem = if (build) systems.acquire("preparation", "preparation environment", preparationFallback)
                else SystemDirectory(preparationFallback, null)
            preparationSystem.use {
                val diagnostics = listOfNotNull(workerSystem.diagnostic, preparationSystem.diagnostic)
                report.update { it.copy(diagnostics = it.diagnostics + diagnostics) }
                val request = TestRunRequest("", target, build, timeoutMillis, cancellationPath)
                return runPreparation(directory, request, workerSystem.path, preparationSystem.path)
            }
        }
    }

    /** Runs the preparation JVM of the run in [directory], which builds if requested and then runs the test worker. */
    private fun runPreparation(directory: Path, request: TestRunRequest, workerSystem: Path, preparationSystem: Path): TestRunReport {
        val reportPath = directory.resolve("report.json")
        val report = TestReportStore(reportPath)
        val requestPath = directory.resolve("request.json")
        Files.writeString(requestPath, ProtocolJson.encodeRequest(request))
        val classpathFile = directory.resolve("classpath.args")
        val classpath = System.getProperty("java.class.path").replace("\\", "\\\\").replace("\"", "\\\"")
        Files.writeString(classpathFile, "-cp\n\"$classpath\"\n")
        val args = ManagementFactory.getRuntimeMXBean().inputArguments.filterNot {
            it.startsWith("-Djava.awt.headless=") || it.startsWith("-Didea.config.path=") || it.startsWith("-Didea.system.path=") ||
                it.startsWith("-Didea.log.path=") || it.startsWith("-agentlib:") || it.startsWith("-javaagent:")
        }
        val command = listOf(Path.of(System.getProperty("java.home"), "bin", "java").toString()) + args + listOf(
            "-Djava.awt.headless=true",
            "-Didea.config.path=${directory.resolve("idea-config")}",
            "-Didea.system.path=$preparationSystem",
            "-Didea.log.path=${directory.resolve("idea-log")}",
            "@$classpathFile",
            "com.specificlanguages.mops.daemon.TestPreparationKt", requestPath.toString(), reportPath.toString(),
            requireNotNull(access.project.project.basePath), jetbrains.mps.util.PathManager.getHomePath(),
            directory.resolve("selection.properties").toString(), workerSystem.toString(),
        )
        val workerStarted = System.nanoTime()
        val worker = try {
            ProcessBuilder(command).redirectErrorStream(true).redirectOutput(directory.resolve("worker.log").toFile()).start()
        } catch (failure: Exception) {
            report.finish(TestRunConclusion("WORKER_FAILED", complete = false, listOf(failure.toString())))
            return report.snapshot()
        }
        // Only the worker knows when test execution starts, so it enforces the time limit; the daemon handles
        // cancellation and a worker that exits without a final report.
        val limits = TestRunLimits(request.cancellationPath?.let(Path::of), 0)
        val interruption = try {
            limits.watch(running = worker::isAlive)
        } finally {
            if (worker.isAlive) {
                destroyProcessTree(worker.toHandle())
                worker.waitFor()
            }
            worker.outputStream.close()
        }
        val saved = TestReportStore(reportPath)
        if (interruption != null) saved.finish(TestRunConclusion(interruption, complete = false))
        else if (saved.snapshot().outcome == "RUNNING")
            saved.finish(TestRunConclusion("WORKER_FAILED", complete = false,
                listOf("Test worker exited ${worker.exitValue()}; see ${directory.resolve("worker.log")}")))
        saved.recordTiming("workerLifetime", (System.nanoTime() - workerStarted) / 1_000_000)
        return saved.snapshot()
    }
}
