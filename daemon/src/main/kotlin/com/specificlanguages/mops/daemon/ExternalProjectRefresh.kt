package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.vfs.LocalFileSystem
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.ide.platform.watching.ReloadManager
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.project.dependency.GlobalModuleDependenciesManager
import jetbrains.mps.util.PathManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Files
import java.nio.file.Path

/**
 * Refreshes project files, the folders of source modules, and the archives of packaged modules, and replaces runtimes
 * whose Java classpath contents changed between requests. [WatchedFolderRefresh] decides how folders are refreshed.
 */
internal class ExternalProjectRefresh(private val project: Project, private val logger: DaemonLogger) {
    private val folderRefresh = WatchedFolderRefresh()
    private val classPathFingerprints = ClassPathFingerprints()
    private var outputs = snapshot()
    private var archiveStamps: Map<Path, ArchiveStamp?>? = null
    private val foldersWithStoredFilesLoaded = mutableSetOf<Path>()
    private val mpsHome = Path.of(PathManager.getHomePath())

    /** Refreshes external changes and returns how the folders were refreshed. */
    fun refresh(): WatchedFolderRefresh.Pass {
        val mpsProject = project as MPSProject
        val pass = folderRefresh.begin()
        ApplicationManager.getApplication().invokeAndWait {
            val settings = Path.of(requireNotNull(mpsProject.project.basePath)).resolve(".mps")
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(settings)?.let { pass.refresh(listOf(it)) }
        }
        // The component store reloads project membership separately from MPS's model/module reload sessions.
        runBlocking { StoreReloadManager.getInstance(mpsProject.project).reloadChangedStorageFiles() }
        ApplicationManager.getApplication().invokeAndWait {
            refreshModuleFolders(pass)
            refreshModuleArchives()
            ReloadManager.getInstance().flush()
            val current = snapshot()
            val changed = current.filter { (module, files) -> outputs[module] != files }.keys
            if (changed.isNotEmpty()) {
                project.modelAccess.runWriteAction {
                    val modules = changed.mapNotNull { it.resolve(project.repository) }
                    project.getComponent(ClassLoaderManager::class.java).reloadModules(modules)
                }
            }
            outputs = current
        }
        logger.log(pass.summary())
        return pass
    }

    /**
     * Refreshes the local folders that modules are loaded from: project modules and the modules of libraries that point
     * at source folders. The persistent VFS keeps records of these files across daemon restarts, so the first time a
     * folder is seen, the files the VFS has stored under it are loaded into memory before the refresh;
     * docs/test-running-design.md explains why.
     */
    private fun refreshModuleFolders(pass: WatchedFolderRefresh.Pass) {
        val folders = project.modelAccess.computeReadAction { moduleFolders(project, mpsHome) }
            .mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it) }
        folders.filter { foldersWithStoredFilesLoaded.add(it.toNioPath()) }.forEach(::loadStoredFiles)
        pass.refresh(folders)
    }

    /**
     * Refreshes the archives that packaged modules (from the MPS distribution and from libraries) are loaded from: the
     * archive with the deployment descriptor and the archives with the source descriptor and models, usually a
     * separate `-src.jar`. The persistent VFS keeps archive entries and their cached contents across daemon restarts,
     * and refreshing the archive file itself does not revalidate them, so a replaced archive keeps serving the old
     * models until its archive root is refreshed. The first call refreshes every archive because the VFS may predate
     * this daemon; later calls refresh only archives whose size or timestamp on disk changed since the previous call.
     */
    private fun refreshModuleArchives() {
        val current = project.modelAccess.computeReadAction { moduleArchives(project) }.associateWith(ArchiveStamp::of)
        val previous = archiveStamps
        val changed = if (previous == null) {
            current.keys
        } else {
            current.filter { (archive, stamp) -> previous[archive] != stamp }.keys
        }
        changed.forEach(::refreshArchive)
        archiveStamps = current
    }

    /** The size and timestamp of an archive on disk, or `null` for a missing archive. */
    private data class ArchiveStamp(val modified: Long, val size: Long) {
        companion object {
            fun of(archive: Path): ArchiveStamp? =
                if (Files.isRegularFile(archive)) {
                    ArchiveStamp(Files.getLastModifiedTime(archive).toMillis(), Files.size(archive))
                } else null
        }
    }

    private fun snapshot(): Map<SModuleReference, List<Pair<Path, String>>> {
        val classPaths = project.modelAccess.computeReadAction {
            val modules = GlobalModuleDependenciesManager(project.projectModulesWithGenerators)
                .getModules(GlobalModuleDependenciesManager.Deptype.EXECUTE)
            modules.mapNotNull { module ->
                val facet = module.getFacet(JavaModuleFacet::class.java)
                if (facet == null || facet.loadClasses != JavaModuleFacet.LoadClasses.ManagedByMPS) return@mapNotNull null
                // A nested archive entry is replaced when its containing archive changes.
                module.moduleReference to facet.classPath.map { entry -> Path.of(entry.substringBefore("!/")) }
            }.toMap()
        }
        val fingerprints = classPathFingerprints.compute(classPaths.values.flatten())
        return classPaths.mapValues { (_, roots) -> roots.map { it to fingerprints.getValue(it) } }
    }
}
