package com.specificlanguages.mops.daemon

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import jetbrains.mps.ide.platform.watching.ReloadManager
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * Brings the VFS up to date with [folders] and with the archives of packaged modules outside [mpsHome], and lets MPS
 * reload the modules, models, and project settings of [project] that changed. A reused IntelliJ system directory keeps
 * VFS records and file content from earlier runs, and the headless platform does not synchronously refresh them when
 * it opens a project. The archives of the distribution are left out: the system directory is used by one MPS build only.
 *
 * The files the VFS has stored under [folders] are loaded into memory before the refresh; docs/test-running-design.md
 * explains why. `AntTestWorker` in ant-test-adapters does the same in the test worker: keep the two in step.
 */
fun refreshFromDisk(project: MPSProject, folders: List<Path>, mpsHome: Path) {
    val files = folders.mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it) }
    val distribution = mpsHome.toRealPath()
    val archives = project.modelAccess.computeReadAction { moduleArchives(project) }.filterNot { it.startsWith(distribution) }
    ApplicationManager.getApplication().invokeAndWait {
        files.forEach(::loadStoredFiles)
        VfsUtil.markDirtyAndRefresh(false, true, true, *files.toTypedArray())
        archives.forEach(::refreshArchive)
        ReloadManager.getInstance().flush()
    }
    runBlocking { StoreReloadManager.getInstance(project.project).reloadChangedStorageFiles() }
}

/** Loads into memory the files the VFS has stored under [folder], without listing folders on disk. */
internal fun loadStoredFiles(folder: VirtualFile) {
    VfsUtilCore.visitChildrenRecursively(folder, object : VirtualFileVisitor<Unit>() {
        override fun getChildrenIterable(file: VirtualFile): Iterable<VirtualFile> =
            (file as NewVirtualFile).iterInDbChildren()
    })
}
