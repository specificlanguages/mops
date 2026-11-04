package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.ide.platform.watching.ReloadManager
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.project.dependency.GlobalModuleDependenciesManager
import jetbrains.mps.util.PathManager
import jetbrains.mps.vfs.refresh.DefaultCachingContext
import kotlinx.coroutines.runBlocking
import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Files
import java.nio.file.FileVisitOption.FOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Refreshes project files, the folders of source modules, and the archives of packaged modules, and replaces runtimes
 * whose Java classpath contents changed between requests.
 */
internal class ExternalProjectRefresh(private val project: Project) {
    private var outputs = snapshot()
    private var archiveStamps: Map<Path, ArchiveStamp?>? = null
    private val foldersWithStoredFilesLoaded = mutableSetOf<Path>()
    private val mpsHome = Path.of(PathManager.getHomePath())

    fun refresh() {
        val mpsProject = project as MPSProject
        ApplicationManager.getApplication().invokeAndWait {
            val settings = Path.of(requireNotNull(mpsProject.project.basePath)).resolve(".mps")
            mpsProject.fileSystem.getFile(settings.toString()).refresh(DefaultCachingContext(true, true))
        }
        // The component store reloads project membership separately from MPS's model/module reload sessions.
        runBlocking { StoreReloadManager.getInstance(mpsProject.project).reloadChangedStorageFiles() }
        ApplicationManager.getApplication().invokeAndWait {
            refreshModuleFolders()
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
    }

    /**
     * Refreshes the local folders that modules are loaded from: project modules and the modules of libraries that point
     * at source folders. The persistent VFS keeps records of these files across daemon restarts, so the first time a
     * folder is seen, the files the VFS has stored under it are loaded into memory before the refresh;
     * docs/test-running-design.md explains why.
     */
    private fun refreshModuleFolders() {
        val folders = project.modelAccess.computeReadAction { moduleFolders(project, mpsHome) }
            .mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it) }
        folders.filter { foldersWithStoredFilesLoaded.add(it.toNioPath()) }.forEach(::loadStoredFiles)
        VfsUtil.markDirtyAndRefresh(false, true, true, *folders.toTypedArray())
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

    // Replaced classes and libraries can retain both their timestamps and sizes.
    private fun snapshot(): Map<SModuleReference, List<Pair<Path, String>>> =
        project.modelAccess.computeReadAction {
            val fingerprints = mutableMapOf<Path, String>()
            val modules = GlobalModuleDependenciesManager(project.projectModulesWithGenerators)
                .getModules(GlobalModuleDependenciesManager.Deptype.EXECUTE)
            modules.mapNotNull { module ->
                val facet = module.getFacet(JavaModuleFacet::class.java)
                if (facet == null || facet.loadClasses != JavaModuleFacet.LoadClasses.ManagedByMPS) return@mapNotNull null
                val files = facet.classPath.map { entry ->
                    // A nested archive entry is replaced when its containing archive changes.
                    val path = Path.of(entry.substringBefore("!/"))
                    path to fingerprints.getOrPut(path) { fingerprint(path) }
                }
                module.moduleReference to files
            }.toMap()
        }

    private fun fingerprint(root: Path): String {
        if (!Files.exists(root)) return "missing"
        val digest = MessageDigest.getInstance("SHA-256")
        val files = if (Files.isDirectory(root)) {
            Files.walk(root, FOLLOW_LINKS).use { paths -> paths.filter(Files::isRegularFile).sorted().toList() }
        } else listOf(root)
        val buffer = ByteArray(8192)
        for (file in files) {
            digest.update(root.relativize(file).toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(Files.size(file).toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            Files.newInputStream(file).use { input ->
                var count = input.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}
