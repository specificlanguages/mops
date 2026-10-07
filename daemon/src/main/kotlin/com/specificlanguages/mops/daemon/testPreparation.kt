package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import de.itemis.mps.gradle.project.loader.EnvironmentKind
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
        report.finish("PARENT_TERMINATED", false)
        ProcessHandle.current().descendants().toList().asReversed().forEach { it.destroyForcibly() }
        Runtime.getRuntime().halt(1)
    }
    thread(name = "test-worker-watchdog", isDaemon = true) {
        while (true) {
            val reason = when {
                request.cancellationPath?.let { Files.exists(Path.of(it)) } == true -> "CANCELLED"
                request.deadlineMillis != 0L && System.currentTimeMillis() >= request.deadlineMillis -> "TIMED_OUT"
                else -> null
            }
            if (reason != null) {
                report.finish(reason, false)
                ProcessHandle.current().descendants().toList().asReversed().forEach { it.destroyForcibly() }
                Runtime.getRuntime().halt(1)
            }
            Thread.sleep(100)
        }
    }
    try {
        if (!request.build) {
            report.endTiming()
            report.phase("DISCOVERY")
            val selection = AntTestSelection.load(Path.of(args[4]))
            AntTestExecution(Path.of(args[3]), Path.of(args[1]).parent, report).run(Path.of(args[2]), selection)
            Runtime.getRuntime().halt(if (report.snapshot().successful) 0 else 1)
        }
        ProjectLoader.build {
            environmentKind = EnvironmentKind.IDEA
            environmentConfig {
                testMode = false
                addPluginsRecursivelyFrom(Path.of(args[3], "plugins"))
            }
        }.executeWithProject(Path.of(args[2]).toFile()) { _, opened ->
            report.endTiming()
            val project = opened as MPSProject
            val access = JetBrainsMpsAccess(project, DaemonLogger())
            report.phase("DISCOVERY")
            val selection = access.read { access.resolveTestSelection(request.target) }
            val modules = access.read {
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
                val names = access.read { modules.map { it.moduleReference.toString() } }
                val build = ProjectMake(project).makeModules(names)
                report.update { it.copy(build = build) }
                if (build.outcome == MakeOutcome.FAILED) {
                    report.finish("BUILD_FAILED", true, "Build failed for selection ${request.target.ifEmpty { listOf("project") }}")
                    return@executeWithProject
                }
                access.extra { saveProject() }
                report.endTiming()
            }
            report.phase("DISCOVERY")
            AntTestExecution(Path.of(args[3]), Path.of(args[1]).parent, report)
                .run(Path.of(args[2]), AntTestSelection.resolve(access, request.target))
        }
    } catch (failure: Throwable) {
        failure.printStackTrace()
        val phase = report.snapshot().phase
        report.finish(if (phase == "BUILD") "BUILD_FAILED" else if (phase == "EXECUTION") "WORKER_FAILED" else "DISCOVERY_FAILED",
            phase != "EXECUTION", failure.stackTraceToString())
    }
    Runtime.getRuntime().halt(if (report.snapshot().successful) 0 else 1)
}
