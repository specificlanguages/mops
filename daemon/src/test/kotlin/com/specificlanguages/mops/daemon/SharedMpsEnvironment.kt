package com.specificlanguages.mops.daemon

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.project.DumbService
import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.launcher.MpsLaunchArgs
import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.ProjectLoader
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.tool.environment.Environment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.SynchronousQueue
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * Boots the daemon's MPS environment once per test JVM and keeps it open until JVM shutdown.
 *
 * Initialization is lazy so test runs that never touch MPS semantics do not pay the boot. The environment is the same
 * IDEA-flavored environment the daemon runs in production, so [JetBrainsMpsAccess] behaves identically here and there.
 * The shared project must stay unmodified; tests that mutate models or project files use [withProjectCopy].
 */
object SharedMpsEnvironment {

    private const val FIXTURE_PROJECT_NAME = "mps-json"
    private const val SHUTDOWN_TIMEOUT_MILLIS = 10_000L

    private val shutdown = CountDownLatch(1)

    private val environment: Environment by lazy { boot() }

    val sharedProjectPath: Path by lazy {
        copyFixtureProject(Files.createTempDirectory("mops-shared-project"))
    }

    private var openSharedProject: Project? = null

    val sharedMpsAccess: MpsAccess
        get() = JetBrainsMpsAccess(ensureSharedProject(), DaemonLogger())

    /**
     * Runs [block] against a fresh copy of the fixture project, opened after [prepare] has had a chance to mutate the
     * copied project files, and closes the project afterwards.
     *
     * The shared project is closed first: the MPS repository is environment-global and keyed by module id, so while
     * the shared project is open, a copy's identical modules would resolve to the already-loaded instances and the
     * copy's file mutations would be invisible.
     */
    fun <T> withProjectCopy(
        projectName: String = FIXTURE_PROJECT_NAME,
        prepare: (Path) -> Unit = {},
        block: (MpsAccess, Path) -> T,
    ): T =
        withOpenProjectCopy(projectName, prepare) { project, projectPath ->
            block(JetBrainsMpsAccess(project, DaemonLogger()), projectPath)
        }

    /**
     * Like [withProjectCopy] but hands [block] the opened [Project] directly, for tests that need project-level state
     * (such as its **Project Modules**) rather than the [MpsAccess] read/write surface.
     */
    fun <T> withOpenProjectCopy(
        projectName: String = FIXTURE_PROJECT_NAME,
        prepare: (Path) -> Unit = {},
        block: (Project, Path) -> T,
    ): T {
        closeSharedProject()
        val projectPath = copyFixtureProject(Files.createTempDirectory("mops-project-copy"), projectName)
        prepare(projectPath)
        val project = openProject(projectPath)
        try {
            return block(project, projectPath)
        } finally {
            environment.closeProject(project)
            unloadOrphanedLanguageRuntimes()
        }
    }

    // Closing the copy unregisters its modules from the environment-global repository, but any module the copy *made*
    // keeps its compiled class runtime resident — keyed by module id, past the module's removal. A later test that opens
    // a project sharing that module id would then see the language as loaded though it built nothing, so, e.g., a copy
    // that makes a language pollutes every subsequent test in the JVM. `closeProject` does not drive the module through
    // the class-loading unload path and neither time nor GC reconciles it; only an explicit reload does. reloadAll
    // reconciles the class-loading registry against the (now smaller) repository and evicts those orphaned runtimes, so
    // each copy starts from a clean slate. See docs/mps/module-runtime-unloading.md.
    private fun unloadOrphanedLanguageRuntimes() {
        val repository = MPSModuleRepository.getInstance()
        repository.modelAccess.runWriteAction {
            ClassLoaderManager.getInstance().reloadAll(EmptyProgressMonitor())
        }
    }

    private fun ensureSharedProject(): Project =
        openSharedProject ?: openProject(sharedProjectPath).also { openSharedProject = it }

    private fun closeSharedProject() {
        openSharedProject?.let { project ->
            environment.closeProject(project)
            openSharedProject = null
        }
    }

    // Mirrors ProjectLoader.withOpenProject, which the daemon boots through: pending platform events must be
    // flushed before the project's models become visible. Find operations are index-backed, so the project must
    // additionally leave dumb mode before they return results; the daemon gets that time for free while it sets up
    // its socket, but tests query immediately after opening.
    private fun openProject(projectPath: Path): Project {
        val project = environment.openProject(projectPath.toFile())
        environment.flushAllEvents()
        if (project is MPSProject) {
            DumbService.getInstance(project.project).waitForSmartMode()
        }
        return project
    }

    private fun boot(): Environment {
        val mpsHome = requiredPathProperty("test.mpsHome")
        applyMpsSystemProperties()

        val handoff = SynchronousQueue<Any>()
        val environmentThread = Thread({
            try {
                ProjectLoader
                    .build {
                        environmentKind = EnvironmentKind.IDEA
                        environmentConfig { addPluginsRecursivelyFrom(mpsHome.resolve("plugins")) }
                    }
                    .execute { environment ->
                        handoff.put(environment)
                        shutdown.await()
                    }
            } catch (throwable: Throwable) {
                handoff.put(throwable)
            }
        }, "shared-mps-environment")
        environmentThread.isDaemon = true
        environmentThread.start()

        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown.countDown()
            environmentThread.join(SHUTDOWN_TIMEOUT_MILLIS)
        })

        return when (val booted = handoff.take()) {
            is Environment -> booted.also { allowSecondProjectWithoutDialog() }
            is Throwable -> throw IllegalStateException("MPS environment failed to boot", booted)
            else -> error("unexpected environment handoff: $booted")
        }
    }

    // With a project already open, opening another one would pop the "Where would you like to open the project?"
    // dialog, which the headless environment turns into a project load failure.
    private fun allowSecondProjectWithoutDialog() {
        GeneralSettings.getInstance().confirmOpenNewProject = GeneralSettings.OPEN_PROJECT_NEW_WINDOW
    }

    // idea.home.path / idea.config.path / idea.system.path are supplied as launch-time -D arguments by the Gradle test
    // task, not set here. The IntelliJ platform locks on idea.config.path / idea.system.path and caches them on first
    // PathManager access, so a runtime System.setProperty can lose that race and fall back to a shared per-product
    // directory, colliding across worktrees. See the daemon build script's test task. The remaining MpsLaunchArgs -D
    // properties are read later than PathManager initialization, so setting them at runtime here is safe.
    private fun applyMpsSystemProperties() {
        val mpsHome = requiredPathProperty("test.mpsHome")
        MpsLaunchArgs.getJvmArgsFor(mpsHome)
            .filter { it.startsWith("-D") && it.contains('=') }
            .forEach { argument ->
                val (key, value) = argument.removePrefix("-D").split("=", limit = 2)
                System.setProperty(key, value)
            }
    }

    private fun copyFixtureProject(targetParent: Path, projectName: String = FIXTURE_PROJECT_NAME): Path {
        val source = requiredPathProperty("test.projectsDir").resolve(projectName)
        require(Files.isDirectory(source)) { "missing fixture project: $source" }
        val target = targetParent.resolve(projectName)
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val relative = source.relativize(path)
                if (isGeneratedArtifact(relative)) return@forEach
                val destination = target.resolve(relative.pathString)
                if (Files.isDirectory(path)) {
                    destination.createDirectories()
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        return target
    }

    // MPS build outputs (source_gen, classes_gen, and their .caches siblings) must never enter a test's working copy:
    // opening a copy that already carries a compiled language would load that language's runtime and make its concepts
    // resolve, so the copy would no longer reproduce an as-yet-unmade language.
    private fun isGeneratedArtifact(relative: Path): Boolean =
        (0 until relative.nameCount).any { index ->
            val segment = relative.getName(index).pathString
            segment.endsWith("_gen") || segment.endsWith("_gen.caches")
        }

    private fun requiredPathProperty(name: String): Path =
        Path.of(requireNotNull(System.getProperty(name)) { "missing system property $name" })
}
