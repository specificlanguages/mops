package com.specificlanguages.mops.cli

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * Test project copies and their daemons, shared by the tests of one test class.
 *
 * Register it on a static field with `@RegisterExtension`. A daemon serves one project root, so sharing a daemon means
 * sharing a project copy. [project] returns the same copy for the same `copy` key every time it is called while the
 * class runs. All copies use one daemon home, the first command against a copy starts its daemon, and later commands
 * reuse it. After the class, the daemons are stopped and the copies deleted, so a test JVM keeps alive only the
 * daemons of the class that is running.
 *
 * Tests that share a copy see each other's changes to it, in any order, so they must not depend on the state that
 * other tests leave behind. A test that needs a project nobody else touches passes its own `copy` key. A test that
 * needs a private daemon home, such as one that stops, kills, or fails to start a daemon, does not use this class: it
 * creates a daemon home in its own `@TempDir` and calls [stopDaemons] itself.
 */
class SharedDaemons : AfterAllCallback {
    private var root: Path? = null
    private val projects = mutableMapOf<String, SharedProject>()

    /** Returns the copy of test project [name] stored under [copy], creating it on first use. */
    fun project(name: String, copy: String = name): SharedProject {
        val root = root ?: Files.createTempDirectory("mops-shared-daemons").also { root = it }
        val project = projects.getOrPut(copy) {
            SharedProject(name, copyTestProject(name, root.resolve("projects").resolve(copy)),
                root.resolve("daemon-home").createDirectories())
        }
        check(project.name == name) { "copy $copy holds test project ${project.name}, not $name" }
        return project
    }

    override fun afterAll(context: ExtensionContext) {
        for (project in projects.values) stopDaemons(project.root, project.daemonHome)
        projects.clear()
        root?.toFile()?.deleteRecursively()
        root = null
    }
}

/** A copy of test project [name] at [root] whose daemon lives in [daemonHome]; see [SharedDaemons]. */
class SharedProject(val name: String, val root: Path, val daemonHome: Path) {
    /** Runs the CLI in this JVM against this project and its daemon. */
    fun run(vararg args: String): CliResult =
        runCommandLine(root, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(), *args)
}
