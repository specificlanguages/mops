/**
 * Adapter. Hides: how the VFS refreshes a folder, by walking it or from the native file watcher's events.
 */
package com.specificlanguages.mops.daemon.filewatcher

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Path

/** Refreshes the folders of [plan] recursively. A folder that the VFS does not know is skipped. Must run on the EDT. */
internal fun executeFolderRefresh(plan: FolderRefreshPlan) {
    val walk = virtualFiles(plan.walk)
    if (walk.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, true, true, *walk.toTypedArray())
    val fromEvents = virtualFiles(plan.fromEvents)
    if (fromEvents.isNotEmpty()) LocalFileSystem.getInstance().refreshFiles(fromEvents, false, true, null)
}

private fun virtualFiles(folders: List<Path>) = folders.mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it) }
