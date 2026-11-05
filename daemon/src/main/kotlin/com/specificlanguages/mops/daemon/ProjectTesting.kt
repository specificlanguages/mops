package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.launcher.MpsBuildProperties
import com.specificlanguages.mops.protocol.*
import jetbrains.mps.progress.EmptyProgressMonitor
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
        if (build && !build(target, report, TestRunLimits(cancellationPath?.let(Path::of), executionTimeoutMillis = 0))) return report.snapshot()
        val mpsHome = Path.of(jetbrains.mps.util.PathManager.getHomePath())
        val mpsBuild = requireNotNull(MpsBuildProperties.fromMpsHome(mpsHome)?.buildNumber) {
            "MPS build number not found in ${mpsHome.resolve("build.properties")}"
        }
        TestWorkerSystemDirectory(testSystemCache, mpsBuild).acquire(directory.resolve("test-system")).use { workerSystem ->
            workerSystem.diagnostic?.let { diagnostic -> report.update { it.copy(diagnostics = it.diagnostics + diagnostic) } }
            val request = TestRunRequest("", target, build, timeoutMillis, cancellationPath)
            return runSupervisor(directory, request, workerSystem.path)
        }
    }

    /**
     * Makes the modules of the Test Selection [target] and saves the project. Returns false when the run must stop: the
     * build failed or the run was cancelled, and [report] says so.
     */
    private fun build(target: List<String>, report: TestReportStore, limits: TestRunLimits): Boolean {
        val project = access.project
        report.phase("BUILD")
        report.beginTiming("build")
        try {
            val (description, modules) = project.modelAccess.computeReadAction {
                val selection = AntTestSelection.select(project, target)
                AntTestSelection.describe(selection) to AntTestSelection.modules(project, selection).map { it.moduleReference.toString() }
            }
            val cancellation = object : EmptyProgressMonitor() {
                override fun isCanceled() = super.isCanceled() || limits.interruption() != null
            }
            val build = ProjectMake(project).makeModules(modules, cancellation)
            report.update { it.copy(build = build) }
            limits.interruption()?.let { interruption ->
                report.finish(TestRunConclusion(interruption, complete = false))
                return false
            }
            if (build.outcome == MakeOutcome.FAILED) {
                report.finish(TestRunConclusion("BUILD_FAILED", complete = true, listOf("Build failed for selection: $description")))
                return false
            }
            access.extra { saveProject() }
        } catch (failure: Exception) {
            report.finish(TestRunConclusion("BUILD_FAILED", complete = true, listOf(failure.stackTraceToString())))
            return false
        }
        report.endTiming()
        report.phase("DISCOVERY")
        return true
    }

    /** Runs the test supervisor JVM of the run in [directory], which runs the Ant test worker. */
    private fun runSupervisor(directory: Path, request: TestRunRequest, workerSystem: Path): TestRunReport {
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
            "@$classpathFile",
            "com.specificlanguages.mops.daemon.TestSupervisorKt", requestPath.toString(), reportPath.toString(),
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
