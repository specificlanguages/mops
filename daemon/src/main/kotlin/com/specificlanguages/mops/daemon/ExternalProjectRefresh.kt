package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.extapi.persistence.FileBasedModelRoot
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.ide.platform.watching.ReloadManager
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.project.dependency.GlobalModuleDependenciesManager
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.refresh.DefaultCachingContext
import jetbrains.mps.vfs.refresh.FileEventProcessor
import jetbrains.mps.vfs.refresh.FileSystemEvent
import kotlinx.coroutines.runBlocking
import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Files
import java.nio.file.FileVisitOption.FOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Refreshes project files and the archives of packaged modules, and replaces runtimes whose Java classpath contents
 * changed between requests.
 */
internal class ExternalProjectRefresh(private val project: Project) {
    private var outputs = snapshot()
    private var archiveStamps: Map<Path, ArchiveStamp?>? = null

    fun refresh() {
        val mpsProject = project as MPSProject
        ApplicationManager.getApplication().invokeAndWait {
            val settings = Path.of(requireNotNull(mpsProject.project.basePath)).resolve(".mps")
            mpsProject.fileSystem.getFile(settings.toString()).refresh(DefaultCachingContext(true, true))
        }
        // The component store reloads project membership separately from MPS's model/module reload sessions.
        runBlocking { StoreReloadManager.getInstance(mpsProject.project).reloadChangedStorageFiles() }
        ApplicationManager.getApplication().invokeAndWait {
            val roots = project.modelAccess.computeReadAction {
                project.projectModules.mapNotNull { (it as? AbstractModule)?.descriptorFile?.parent }.distinct()
            }
            roots.forEach { it.refresh(DefaultCachingContext(true, true)) }
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
     * Refreshes the archives that packaged modules (from the MPS distribution and from libraries) are loaded from: the
     * archive with the deployment descriptor and the archives with the source descriptor and models, usually a
     * separate `-src.jar`. The persistent VFS keeps archive entries and their cached contents across daemon restarts,
     * and refreshing the archive file itself does not revalidate them, so a replaced archive keeps serving the old
     * models until its archive root is refreshed. The first call refreshes every archive because the VFS may predate
     * this daemon; later calls refresh only archives whose size or timestamp on disk changed since the previous call.
     */
    private fun refreshModuleArchives() {
        val current = project.modelAccess.computeReadAction { moduleArchives() }.associateWith(ArchiveStamp::of)
        val previous = archiveStamps
        val changed = if (previous == null) {
            current.keys
        } else {
            current.filter { (archive, stamp) -> previous[archive] != stamp }.keys
        }
        changed.forEach(::refreshArchive)
        if (changed.isNotEmpty()) notifyModelSources(changed)
        archiveStamps = current
    }

    /**
     * Refreshes the entries of [archive], then the archive file itself. The archive file system caches the entry list
     * of an archive, so its cache is cleared first or the refresh finds no changed entries. MPS ignores events from
     * archive file systems and learns about archive changes only from the event for the archive file, so the entries
     * must be current when that event arrives.
     */
    private fun refreshArchive(archive: Path) {
        val local = LocalFileSystem.getInstance().findFileByNioFile(archive) ?: return
        val root = JarFileSystem.getInstance().getJarRootForLocalFile(local)
        if (root != null) {
            JarFileSystem.getInstance().clearArchiveCache(root)
            VfsUtil.markDirtyAndRefresh(false, true, true, root)
        }
        VfsUtil.markDirtyAndRefresh(false, false, false, local)
    }

    /**
     * Tells the file-backed models inside [archives] that their files changed. MPS passes the event for an archive file
     * to the models inside it, but a model's [FileDataSource] reacts only to an event that names its own file, so a
     * model in a separate source archive never reloads. (A model in the archive with the module descriptor does reload,
     * through the reload of its module.) Each model compares timestamps itself and reloads only if its entry changed.
     */
    private fun notifyModelSources(archives: Set<Path>) {
        val sources = project.modelAccess.computeReadAction {
            project.repository.modules.flatMap { it.models }.mapNotNull { it.source as? FileDataSource }
                .filter { it.file.isInZipArchive && Path.of(it.file.path.substringBefore("!/")) in archives }
        }
        sources.forEach { source -> source.update(EmptyProgressMonitor(), ChangedFile(source.file)) }
    }

    /** A file system event for a single changed file. */
    private class ChangedFile(private val file: IFile) : FileSystemEvent {
        override fun getCreated(): Set<IFile> = emptySet()
        override fun getRemoved(): Set<IFile> = emptySet()
        override fun getChanged(): Set<IFile> = setOf(file)
        override fun notify(anotherListener: FileEventProcessor) = anotherListener.update(EmptyProgressMonitor(), this)
    }

    private fun moduleArchives(): Set<Path> =
        project.repository.modules.filterIsInstance<AbstractModule>().flatMap { module ->
            val contentDirectories = module.modelRoots.filterIsInstance<FileBasedModelRoot>().map { it.contentDirectory }
            listOf(module.descriptorFile, module.moduleSourceDir) + contentDirectories
        }.filterNotNull().filter { it.isInZipArchive }.map { Path.of(it.path.substringBefore("!/")) }.toSet()

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
