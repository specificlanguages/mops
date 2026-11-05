package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.local.PluggableFileWatcher
import java.io.IOException
import java.nio.file.Path
import java.time.Duration

/**
 * Refreshes local folders before a request, relying on the native file watcher when it can be trusted.
 *
 * A forced refresh (a full walk) visits every file the VFS has loaded under a folder. With the watcher, a fence first
 * makes sure the watcher has reported every change made before the request, and a non-forced recursive refresh then
 * visits only the files the watcher reported. The full walk stays the fallback: when the watcher is not operational or
 * has reported a failure, when the fence times out, and on the first refresh after the watch roots changed or
 * `fsnotifier` was started again. A folder is also walked fully the first time it is refreshed and whenever it lies
 * outside every recursive watch root.
 *
 * See the mps-api-research note native-file-watcher-in-headless-mps.md.
 */
internal class WatchedFolderRefresh {
    private var lastChangeCount: Long? = null
    private val refreshedFolders = mutableSetOf<Path>()

    /** Prepares the refresh for one request. Must run outside the EDT, before [Pass.refresh]. */
    fun begin(): Pass {
        val started = System.nanoTime()
        val watcher = HeadlessWatcher.find()
        val fileWatcher = (LocalFileSystem.getInstance() as LocalFileSystemImpl).fileWatcher
        val reason = when {
            watcher == null || !fileWatcher.isOperational -> "the file watcher is not operational"
            !awaitSettledRoots(fileWatcher::isSettingRoots) -> "the file watcher is still setting its roots"
            else -> fenceAndCompare(watcher)
        }
        return Pass(reason, watcher?.recursiveRoots().orEmpty(), Duration.ofNanos(System.nanoTime() - started))
    }

    private fun fenceAndCompare(watcher: HeadlessWatcher): String? {
        val fence = FileWatcherFence.instance
        val fenced = fence?.pass(FENCE_TIMEOUT) ?: false
        // The watcher reports failures as notifications that are delivered on the EDT.
        ApplicationManager.getApplication().invokeAndWait {}
        val changeCount = watcher.changeCount()
        val previous = lastChangeCount
        lastChangeCount = changeCount
        return when {
            changeCount == null -> "the file watcher reported a failure"
            fence == null -> "the file watcher event hook is not available"
            !fenced -> "the file watcher did not report the fence within ${FENCE_TIMEOUT.toMillis()} ms"
            previous == null -> "this is the first refresh"
            previous != changeCount -> "the watch roots changed or the file watcher restarted"
            else -> null
        }
    }

    private fun awaitSettledRoots(settingRoots: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + ROOTS_TIMEOUT.toNanos()
        while (settingRoots()) {
            if (System.nanoTime() > deadline) return false
            Thread.sleep(POLL_MILLIS)
        }
        return true
    }

    /**
     * The refresh of one request. [fullWalkReason] is `null` when the watcher is trusted and says otherwise why every
     * folder is walked.
     */
    inner class Pass(val fullWalkReason: String?, private val recursiveRoots: List<Path>, private val preparation: Duration) {
        private var walked = 0
        private var watched = 0
        private var refreshing = Duration.ZERO

        /** Refreshes [folders] recursively. Must run on the EDT. */
        fun refresh(folders: Collection<VirtualFile>) {
            val started = System.nanoTime()
            val (watchedFolders, walkedFolders) = folders.partition { folder ->
                val path = folder.toNioPath()
                fullWalkReason == null && path in refreshedFolders && isWatched(path)
            }
            refreshedFolders += folders.map { it.toNioPath() }
            if (walkedFolders.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, true, true, *walkedFolders.toTypedArray())
            if (watchedFolders.isNotEmpty()) LocalFileSystem.getInstance().refreshFiles(watchedFolders, false, true, null)
            walked += walkedFolders.size
            watched += watchedFolders.size
            refreshing += Duration.ofNanos(System.nanoTime() - started)
        }

        private fun isWatched(folder: Path): Boolean {
            val real = try {
                folder.toRealPath()
            } catch (_: IOException) {
                return false
            }
            return recursiveRoots.any(real::startsWith)
        }

        /** A line for the daemon log that names how the folders were refreshed and what it cost. */
        fun summary(): String {
            val mode = if (fullWalkReason == null) "file watcher events" else "a full walk because $fullWalkReason"
            return "refreshed external changes using $mode: $watched folders from watcher events, $walked walked; " +
                "watcher wait ${preparation.toMillis()} ms, refresh ${refreshing.toMillis()} ms"
        }
    }

    /** The plugin's `HeadlessNativeFileWatcher`, which is loaded by the plugin class loader and read by reflection. */
    private class HeadlessWatcher(private val instance: PluggableFileWatcher) {
        fun changeCount(): Long? = instance.javaClass.getMethod("changeCount").invoke(instance) as Long?

        fun recursiveRoots(): List<Path> =
            (instance.javaClass.getMethod("recursiveRoots").invoke(instance) as List<*>).map { Path.of(it as String) }

        companion object {
            private const val CLASS_NAME = "com.specificlanguages.mops.daemon.plugin.HeadlessNativeFileWatcher"

            fun find(): HeadlessWatcher? =
                PluggableFileWatcher.EP_NAME.extensionList.firstOrNull { it.javaClass.name == CLASS_NAME }
                    ?.let(::HeadlessWatcher)
        }
    }

    private companion object {
        val FENCE_TIMEOUT: Duration = Duration.ofSeconds(2)
        val ROOTS_TIMEOUT: Duration = Duration.ofSeconds(10)
        const val POLL_MILLIS = 10L
    }
}
