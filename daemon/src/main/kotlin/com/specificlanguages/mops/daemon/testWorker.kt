package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.ProjectLoader
import groovy.lang.Binding
import groovy.lang.GroovyShell
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.function.Consumer
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val request = ProtocolJson.decodeRequest(Files.readString(Path.of(args[0]))) as TestRunRequest
    val report = TestReportStore(Path.of(args[1]))
    // The daemon owns the write end of this pipe. EOF also detects a killed parent that remains a zombie.
    thread(name = "test-worker-parent", isDaemon = true) {
        while (System.`in`.read() != -1) { }
        report.finish("PARENT_TERMINATED", false)
        ProcessHandle.current().descendants().forEach { it.destroyForcibly() }
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
                ProcessHandle.current().descendants().forEach { it.destroyForcibly() }
                Runtime.getRuntime().halt(1)
            }
            Thread.sleep(100)
        }
    }
    try {
        ProjectLoader.build {
            environmentKind = EnvironmentKind.IDEA
            environmentConfig {
                testMode = false
                addPluginsRecursivelyFrom(Path.of(args[3], "plugins"))
            }
        }.executeWithProject(Path.of(args[2]).toFile()) { environment, opened ->
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
                val names = access.read { modules.map { it.moduleReference.toString() } }
                val build = ProjectMake(project).makeModules(names)
                report.update { it.copy(build = build) }
                if (build.outcome == MakeOutcome.FAILED) {
                    report.finish("BUILD_FAILED", true, "Build failed for selection ${request.target.ifEmpty { listOf("project") }}")
                    return@executeWithProject
                }
                access.extra { saveProject() }
            }
            report.phase("DISCOVERY")
            val manager = requireNotNull(environment.platform.findComponent(ClassLoaderManager::class.java))
            val loaders = access.read {
                listOf("jetbrains.mps.lang.test.junit5", "Testbench").map { name ->
                    val module = project.repository.modules.single { it.moduleName == name }
                    manager.getClassLoader(module)
                }
            }
            val host = TestReportStore::class.java.classLoader
            val bridge = object : ClassLoader(null) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (name.startsWith("groovy.") || name.startsWith("org.codehaus.groovy.")) return host.loadClass(name)
                    for (loader in loaders) {
                        try { return loader.loadClass(name) } catch (_: ClassNotFoundException) { }
                    }
                    return host.loadClass(name)
                }
                override fun getResources(name: String) = Collections.enumeration(loaders.flatMap { it.getResources(name).toList() }.distinct())
                override fun getResource(name: String) = loaders.firstNotNullOfOrNull { it.getResource(name) } ?: host.getResource(name)
            }
            val bindings = Binding(mapOf(
                "environment" to environment, "project" to project, "selection" to selection,
                "projectDirectory" to args[2],
                "emit" to Consumer<Map<String, Any?>> { report.event(it) },
                "executing" to Runnable { report.phase("EXECUTION") },
            ))
            val script = requireNotNull(host.getResource("testing/RunTests.groovy")).readText()
            GroovyShell(bridge, bindings).evaluate(script, "RunTests.groovy")
            val snapshot = report.snapshot()
            report.finish(if (snapshot.results.any { it.status == "FAILED" }) "TEST_FAILED" else "SUCCESS", true)
        }
    } catch (failure: Throwable) {
        failure.printStackTrace()
        val phase = report.snapshot().phase
        report.finish(if (phase == "BUILD") "BUILD_FAILED" else if (phase == "EXECUTION") "WORKER_FAILED" else "DISCOVERY_FAILED",
            phase != "EXECUTION", failure.stackTraceToString())
    }
    Runtime.getRuntime().halt(if (report.snapshot().successful) 0 else 1)
}
