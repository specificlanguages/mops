package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.Macro
import de.itemis.mps.gradle.project.loader.ProjectLoader
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val request = ProtocolJson.decodeRequest(Files.readString(Path.of(args[0]))) as TestRunRequest
    val report = TestReportStore(Path.of(args[1]))
    report.beginTiming("preparationStartup")
    // The daemon owns the write end of this pipe. EOF also detects a killed parent that remains a zombie.
    thread(name = "test-worker-parent", isDaemon = true) {
        while (System.`in`.read() != -1) { }
        report.finish(TestRunConclusion("PARENT_TERMINATED", false))
        destroyProcessTree(ProcessHandle.current())
        Runtime.getRuntime().halt(1)
    }
    val limits = TestRunLimits(request.cancellationPath?.let(Path::of), request.timeoutMillis)
    thread(name = "test-worker-watchdog", isDaemon = true) {
        val reason = requireNotNull(limits.watch(running = { true }))
        // Record the interruption before killing the Ant JVM, so the execution thread cannot finish the run first.
        report.finish(TestRunConclusion(reason, false))
        destroyProcessTree(ProcessHandle.current())
        Runtime.getRuntime().halt(1)
    }
    try {
        // The daemon resolved the selection, so the worker sees the repository and path macros the daemon loaded.
        val antSelection = AntTestSelection.load(Path.of(args[4]))
        fun runTests() = AntTestExecution(Path.of(args[3]), Path.of(args[1]).parent, Path.of(args[5]), report, limits)
            .run(Path.of(args[2]), antSelection)
        if (!request.build) {
            report.endTiming()
            report.phase("DISCOVERY")
            runTests()
            Runtime.getRuntime().halt(if (report.snapshot().successful) 0 else 1)
        }
        ProjectLoader.build {
            environmentKind = EnvironmentKind.IDEA
            environmentConfig {
                testMode = false
                addPluginsRecursivelyFrom(Path.of(args[3], "plugins"))
                macros += antSelection.macros.map { (name, value) -> Macro(name, value) }
            }
        }.executeWithProject(Path.of(args[2]).toFile()) { _, opened ->
            val project = opened as MPSProject
            refreshFromDisk(project, antSelection.sourceFolders(Path.of(args[2])), Path.of(args[3]))
            report.endTiming()
            report.phase("DISCOVERY")
            val selection = project.modelAccess.computeReadAction { AntTestSelection.select(project, request.target) }
            val modules = project.modelAccess.computeReadAction {
                when (selection) {
                    is SModule -> listOf(selection)
                    is SModel -> listOf(requireNotNull(selection.module))
                    is SNode -> listOf(requireNotNull(selection.model?.module))
                    else -> project.projectModulesWithGenerators.toList()
                }
            }
            if (request.build) {
                report.phase("BUILD")
                report.beginTiming("build")
                val names = project.modelAccess.computeReadAction { modules.map { it.moduleReference.toString() } }
                val build = ProjectMake(project).makeModules(names)
                report.update { it.copy(build = build) }
                if (build.outcome == MakeOutcome.FAILED) {
                    val description = project.modelAccess.computeReadAction { AntTestSelection.describe(selection) }
                    report.finish(TestRunConclusion("BUILD_FAILED", true, listOf("Build failed for selection: $description")))
                    return@executeWithProject
                }
                saveProjectSettings(project)
                report.endTiming()
            }
            report.phase("DISCOVERY")
            runTests()
        }
    } catch (failure: Throwable) {
        failure.printStackTrace()
        val phase = report.snapshot().phase
        val outcome = if (phase == "BUILD") "BUILD_FAILED" else if (phase == "EXECUTION") "WORKER_FAILED" else "DISCOVERY_FAILED"
        report.finish(TestRunConclusion(outcome, phase != "EXECUTION", listOf(failure.stackTraceToString())))
    }
    Runtime.getRuntime().halt(if (report.snapshot().successful) 0 else 1)
}
