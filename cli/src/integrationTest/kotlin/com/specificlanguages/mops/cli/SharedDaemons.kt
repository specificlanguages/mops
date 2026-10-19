package com.specificlanguages.mops.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * Test project copies and their daemons, shared by the integration tests in one test JVM.
 *
 * A daemon serves one project root, so sharing a daemon means sharing a project copy. [project] returns the same copy
 * for the same `copy` key every time it is called in a JVM. All copies use one daemon home, the first command against a
 * copy starts its daemon, and later commands reuse it. When the JVM shuts down, the daemons are stopped and the copies
 * deleted.
 *
 * Tests that share a copy see each other's changes to it, in any order, so they must not depend on the state that
 * other tests leave behind. A test that needs a project nobody else touches passes its own `copy` key: it still uses
 * the shared daemon home, but gets a fresh copy and daemon. A test that needs a private daemon home, such as one that
 * stops, kills, or fails to start a daemon, does not use this object: it creates a daemon home in its own `@TempDir`
 * and calls [stopDaemons] itself.
 */
object SharedDaemons {
    private val root: Path by lazy {
        Files.createTempDirectory("mops-shared-daemons").also {
            Runtime.getRuntime().addShutdownHook(Thread(::stopAll, "stop-shared-mops-daemons"))
        }
    }
    private val daemonHome: Path by lazy { root.resolve("daemon-home").createDirectories() }
    private val projects = mutableMapOf<String, SharedProject>()

    /** Returns the JVM-wide copy of test project [name] stored under [copy], creating it on first use. */
    @Synchronized
    fun project(name: String, copy: String = name): SharedProject {
        val project = projects.getOrPut(copy) {
            SharedProject(name, copyTestProject(name, root.resolve("projects").resolve(copy)), daemonHome)
        }
        check(project.name == name) { "copy $copy holds test project ${project.name}, not $name" }
        return project
    }

    @Synchronized
    private fun stopAll() {
        for (project in projects.values) stopDaemons(project.root, project.daemonHome)
        root.toFile().deleteRecursively()
    }
}

/** A copy of test project [name] at [root] whose daemon lives in [daemonHome]; see [SharedDaemons]. */
class SharedProject(val name: String, val root: Path, val daemonHome: Path) {
    /** Runs the CLI in this JVM against this project and its daemon. */
    fun run(vararg args: String): CliResult =
        runCommandLine(root, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(), *args)
}
