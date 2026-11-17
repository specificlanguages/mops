package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.specificlanguages.mops.daemon.filewatcher.FolderRefreshReport
import com.specificlanguages.mops.daemon.filewatcher.FolderRefresher
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.ide.platform.watching.ReloadManager
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.util.PathManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Files
import java.nio.file.Path

/**
 * Adapter. Refreshes project files, the folders of source modules, and the archives of packaged modules, and replaces
 * runtimes whose Java classpath contents changed between requests. [FolderRefresher] refreshes the folders.
 *
 * Hides: how to make MPS see the changes that other programs made on disk to a project and the modules it loads.
 *
 * Keeps a cache (`adapter-cache`): the [RefreshBaseline] of the latest refresh that finished. Each refresh compares with
 * it, and `externalChangePolicy.kt` decides what changed; without it, a refresh would refresh every archive, load the
 * stored files of every folder, and replace every runtime. Not thread-safe: the request gate runs one refresh at a time.
 */
internal class ExternalProjectRefresh(private val project: Project, private val logger: DaemonLogger) {
    private val classPathFingerprints = ClassPathFingerprints()
    private val folderRefresher = FolderRefresher()
    private var baseline = RefreshBaseline(classPaths())
    private val mpsHome = Path.of(PathManager.getHomePath())

    /** Refreshes external changes, logs how the folders were refreshed, and returns that report. */
    fun refresh(): FolderRefreshReport {
        val mpsProject = project as MPSProject
        val previous = baseline
        val folders = folderRefresher.start()
        ApplicationManager.getApplication().invokeAndWait {
            val settings = Path.of(requireNotNull(mpsProject.project.basePath)).resolve(".mps")
            val settingsFolder = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(settings)
            folders.refresh(listOfNotNull(settingsFolder?.toNioPath()))
        }
        // The component store reloads project membership separately from MPS's model/module reload sessions.
        runBlocking { StoreReloadManager.getInstance(mpsProject.project).reloadChangedStorageFiles() }
        lateinit var next: RefreshBaseline
        ApplicationManager.getApplication().invokeAndWait {
            val loaded = refreshModuleFolders(previous.foldersWithStoredFilesLoaded, folders::refresh)
            val archiveStamps = refreshModuleArchives(previous.archiveStamps)
            ReloadManager.getInstance().flush()
            val classPaths = replaceChangedRuntimes(previous.classPaths)
            next = RefreshBaseline(classPaths, archiveStamps, loaded)
        }
        baseline = next
        val report = folders.finish()
        logger.log(report.logLine())
        return report
    }

    /**
     * Refreshes the local folders that modules are loaded from with [refreshFolders]: project modules and the modules of
     * libraries that point at source folders. The persistent VFS keeps records of these files across daemon restarts, so
     * the first time a folder is seen, the files the VFS has stored under it are loaded into memory before the refresh;
     * docs/test-running-design.md explains why. Returns the folders whose stored files are loaded, [loaded] included.
     */
    private fun refreshModuleFolders(loaded: Set<Path>, refreshFolders: (List<Path>) -> Unit): Set<Path> {
        val folders = project.modelAccess.computeReadAction { moduleFolders(project, mpsHome) }
            .mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it) }
            .associateBy { it.toNioPath() }
        val toLoad = foldersToLoad(loaded, folders.keys.toList())
        toLoad.forEach { loadStoredFiles(folders.getValue(it)) }
        refreshFolders(folders.keys.toList())
        return loaded + toLoad
    }

    /**
     * Refreshes the archives that packaged modules (from the MPS distribution and from libraries) are loaded from: the
     * archive with the deployment descriptor and the archives with the source descriptor and models, usually a
     * separate `-src.jar`. The persistent VFS keeps archive entries and their cached contents across daemon restarts,
     * and refreshing the archive file itself does not revalidate them, so a replaced archive keeps serving the old
     * models until its archive root is refreshed. [archivesToRefresh] decides which archives to refresh, comparing with
     * the [previous] stamps. Returns the current stamps.
     */
    private fun refreshModuleArchives(previous: Map<Path, ArchiveStamp?>?): Map<Path, ArchiveStamp?> {
        val current = project.modelAccess.computeReadAction { moduleArchives(project) }.associateWith(::archiveStamp)
        archivesToRefresh(previous, current).forEach(::refreshArchive)
        return current
    }

    /** The size and timestamp of [archive] on disk, or `null` for a missing archive. */
    private fun archiveStamp(archive: Path): ArchiveStamp? =
        if (Files.isRegularFile(archive)) {
            ArchiveStamp(Files.getLastModifiedTime(archive).toMillis(), Files.size(archive))
        } else null

    /**
     * Replaces the runtimes of the modules that [modulesToReload] picks, comparing with the [previous] class paths.
     * Returns the current class paths.
     */
    private fun replaceChangedRuntimes(
        previous: Map<SModuleReference, List<ClassPathRoot>>,
    ): Map<SModuleReference, List<ClassPathRoot>> {
        val current = classPaths()
        val changed = modulesToReload(previous, current)
        if (changed.isNotEmpty()) {
            project.modelAccess.runWriteAction {
                val modules = changed.mapNotNull { it.resolve(project.repository) }
                project.getComponent(ClassLoaderManager::class.java).reloadModules(modules)
            }
        }
        return current
    }

    /** The class path of each module whose classes MPS manages, with a fingerprint of each root. */
    private fun classPaths(): Map<SModuleReference, List<ClassPathRoot>> {
        val classPaths = project.modelAccess.computeReadAction { managedClassPaths(project) }
        val fingerprints = classPathFingerprints.compute(classPaths.values.flatten())
        return classPaths.mapValues { (_, roots) -> roots.map { ClassPathRoot(it, fingerprints.getValue(it)) } }
    }
}
