/**
 * Adapter. Hides: what the VFS keeps about module archives and folders apart from the files themselves (the entries of
 * an archive, the files it has stored under a folder), and how to bring that into memory and up to date.
 */
package com.specificlanguages.mops.daemon

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
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


/** Loads into memory the files the VFS has stored under [folder], without listing folders on disk. */
internal fun loadStoredFiles(folder: VirtualFile) {
    VfsUtilCore.visitChildrenRecursively(folder, object : VirtualFileVisitor<Unit>() {
        override fun getChildrenIterable(file: VirtualFile): Iterable<VirtualFile> =
            (file as NewVirtualFile).iterInDbChildren()
    })
}
