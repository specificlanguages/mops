package com.specificlanguages.mops.daemon

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import jetbrains.mps.extapi.persistence.FileBasedModelRoot
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.Project
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
    project.repository.modules.filterIsInstance<AbstractModule>().flatMap { module ->
        val contentDirectories = module.modelRoots.filterIsInstance<FileBasedModelRoot>().map { it.contentDirectory }
        listOf(module.descriptorFile, module.moduleSourceDir) + contentDirectories
    }.filterNotNull().filter { it.isInZipArchive }.map { Path.of(it.path.substringBefore("!/")) }.toSet()
