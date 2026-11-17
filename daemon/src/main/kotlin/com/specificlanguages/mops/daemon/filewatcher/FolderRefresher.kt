package com.specificlanguages.mops.daemon.filewatcher

import java.io.IOException
import java.nio.file.Path
import java.time.Duration

/**
 * Adapter. Refreshes folders in the VFS, from the native file watcher's events when `folderRefreshPolicy.kt` decides
 * they can be relied on, and by walking the folders otherwise.
 *
 * Hides: how a refresh uses the native file watcher to refresh folders.
 *
 * Keeps a cache (`adapter-cache`): the watcher's change count at the latest refresh that finished, and the folders that
 * refreshes have refreshed. Without it, every folder is walked. Not thread-safe: the request gate runs one refresh at a
 * time.
 */
internal class FolderRefresher {
    private var changeCount: Long? = null
    private var refreshedFolders = emptySet<Path>()

    /**
     * Starts a refresh. Waits until the watcher has reported every change made before this call, or decides to walk
     * every folder. Must run outside the EDT.
     */
    fun start(): Run {
        val started = System.nanoTime()
        val assessment = assessFileWatcher(NativeFileWatcherProbe.find(), changeCount)
        return Run(assessment, Duration.ofNanos(System.nanoTime() - started))
    }

    /** One refresh. Call [finish] when the refresh succeeded, so that the next refresh compares with it. */
    inner class Run internal constructor(private val assessment: WatcherAssessment, watcherWait: Duration) {
        private var refreshed = refreshedFolders
        private var report = FolderRefreshReport((assessment as? WatcherAssessment.FullWalk)?.reason, watcherWait)

        /** Refreshes [folders] recursively. Must run on the EDT. */
        fun refresh(folders: List<Path>) {
            val started = System.nanoTime()
            val plan = planFolderRefresh(folders, assessment, refreshed, ::realPathOrNull)
            refreshed += folders
            executeFolderRefresh(plan)
            report = report.withFolders(plan, Duration.ofNanos(System.nanoTime() - started))
        }

        /** Keeps this refresh for the next one to compare with, and returns how it refreshed folders. */
        fun finish(): FolderRefreshReport {
            changeCount = assessment.changeCount
            refreshedFolders = refreshed
            return report
        }
    }
}

private fun realPathOrNull(path: Path): Path? =
    try {
        path.toRealPath()
    } catch (_: IOException) {
        null
    }
