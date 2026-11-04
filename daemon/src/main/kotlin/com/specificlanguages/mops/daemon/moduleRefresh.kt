package com.specificlanguages.mops.daemon

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import jetbrains.mps.extapi.persistence.FileBasedModelRoot
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.Project
import jetbrains.mps.vfs.IFile
import java.nio.file.Path

/**
 * Refreshes the entries of [archive], then the archive file itself. The archive file system caches the entry list
 * of an archive, so its cache is cleared first or the refresh finds no changed entries. MPS ignores events from
 * archive file systems and learns about archive changes only from the event for the archive file, so the entries
 * must be current when that event arrives.
 */
internal fun refreshArchive(archive: Path) {
    val local = LocalFileSystem.getInstance().findFileByNioFile(archive) ?: return
    val root = JarFileSystem.getInstance().getJarRootForLocalFile(local)
    if (root != null) {
        JarFileSystem.getInstance().clearArchiveCache(root)
        VfsUtil.markDirtyAndRefresh(false, true, true, root)
    }
    VfsUtil.markDirtyAndRefresh(false, false, false, local)
}

/** The archives that the packaged modules in [project]'s repository are loaded from. Requires model read access. */
internal fun moduleArchives(project: Project): Set<Path> =
    project.repository.modules.filterIsInstance<AbstractModule>()
        .flatMap { module -> listOfNotNull(module.descriptorFile) + sourceLocations(module) }
        .filter { it.isInZipArchive }.map { Path.of(it.path.substringBefore("!/")) }.toSet()

/**
 * The local folders that the modules in [project]'s repository are loaded from, outside the MPS distribution at
 * [mpsHome] and none inside another: the folder of the module descriptor, the module source folder, and the content
 * folders of file-based model roots. This covers project modules and the modules of libraries that point at source
 * folders. Requires model read access.
 */
internal fun moduleFolders(project: Project, mpsHome: Path): List<Path> {
    val distribution = setOf(mpsHome.normalize(), mpsHome.toRealPath())
    val folders = project.repository.modules.filterIsInstance<AbstractModule>()
        .flatMap { module -> listOfNotNull(module.descriptorFile?.parent) + sourceLocations(module) }
        .filterNot { it.isInZipArchive }.map { Path.of(it.path).normalize() }
        .filterNot { folder -> distribution.any(folder::startsWith) }.distinct()
    return folders.filter { folder -> folders.none { it != folder && folder.startsWith(it) } }
}

/** The module source folder of [module] and the content folders of its file-based model roots. */
private fun sourceLocations(module: AbstractModule): List<IFile> =
    listOfNotNull(module.moduleSourceDir) +
        module.modelRoots.filterIsInstance<FileBasedModelRoot>().mapNotNull { it.contentDirectory }
