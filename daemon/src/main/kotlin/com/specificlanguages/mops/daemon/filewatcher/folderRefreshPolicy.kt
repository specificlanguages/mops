/**
 * Logic. Hides: when a folder refresh can rely on the native file watcher's events instead of walking the folder.
 *
 * A forced refresh (a full walk) visits every file the VFS has loaded under a folder. With the watcher, a fence first
 * makes sure the watcher has reported every change made before the request, and a non-forced recursive refresh then
 * visits only the files the watcher reported. [FullWalkReason] lists when every folder is walked instead. A folder is
 * also walked the first time it is refreshed and whenever the watcher does not cover it ([WatchCoverage]).
 */
package com.specificlanguages.mops.daemon.filewatcher

import java.nio.file.Path

/** Logic. What [assessFileWatcher] asks the native file watcher. */
internal interface FileWatcherSignals {
    /** Whether `fsnotifier` runs. */
    fun isOperational(): Boolean

    /** Waits until the watcher has set its watch roots. Returns `false` when it is still setting them after a timeout. */
    fun awaitSettledRoots(): Boolean

    /** Waits until the watcher has reported every change made before this call. */
    fun awaitFence(): Fence

    /**
     * A number that changes whenever the watcher may have missed changes, or `null` after it reported a failure or when
     * its restarts cannot be counted.
     */
    fun changeCount(): Long?

    /** How [awaitFence] ended. */
    sealed interface Fence {
        /** The watcher reported the fence, and it reports changes in order with the fence under [coverage]. */
        data class Reported(val coverage: WatchCoverage) : Fence

        /** The watcher did not report the fence in time. */
        data object NotReported : Fence

        /** The watcher's event hook is not available, so there is no fence. */
        data object Unavailable : Fence
    }
}

/**
 * Decides whether this refresh can rely on [watcher], which is `null` when the mops file watcher is not registered.
 * [previousChangeCount] is the [WatcherAssessment.changeCount] of the previous refresh.
 */
internal fun assessFileWatcher(watcher: FileWatcherSignals?, previousChangeCount: Long?): WatcherAssessment {
    if (watcher == null) return WatcherAssessment.FullWalk(FullWalkReason.NOT_REGISTERED, previousChangeCount)
    if (!watcher.isOperational()) return WatcherAssessment.FullWalk(FullWalkReason.NOT_OPERATIONAL, previousChangeCount)
    if (!watcher.awaitSettledRoots()) {
        return WatcherAssessment.FullWalk(FullWalkReason.ROOTS_NOT_SETTLED, previousChangeCount)
    }
    val fence = watcher.awaitFence()
    val changeCount = watcher.changeCount() ?: return WatcherAssessment.FullWalk(FullWalkReason.FAILURE_REPORTED, null)
    val reason = when (fence) {
        FileWatcherSignals.Fence.Unavailable -> FullWalkReason.NO_EVENT_HOOK
        FileWatcherSignals.Fence.NotReported -> FullWalkReason.FENCE_NOT_REPORTED
        is FileWatcherSignals.Fence.Reported -> when (previousChangeCount) {
            null -> FullWalkReason.FIRST_REFRESH
            changeCount -> return WatcherAssessment.Trusted(changeCount, fence.coverage)
            else -> FullWalkReason.ROOTS_CHANGED
        }
    }
    return WatcherAssessment.FullWalk(reason, changeCount)
}

/**
 * Splits [folders] into the folders to walk and the folders to refresh from watcher events. A folder is refreshed from
 * events only when [assessment] trusts the watcher, the folder is in [refreshedBefore], and the watcher covers its
 * [realPath], which is `null` for a folder that cannot be resolved.
 */
internal fun planFolderRefresh(
    folders: List<Path>,
    assessment: WatcherAssessment,
    refreshedBefore: Set<Path>,
    realPath: (Path) -> Path?,
): FolderRefreshPlan {
    val coverage = (assessment as? WatcherAssessment.Trusted)?.coverage
    val (fromEvents, walk) = folders.partition { folder ->
        coverage != null && folder in refreshedBefore && realPath(folder)?.let(coverage::covers) == true
    }
    return FolderRefreshPlan(walk = walk, fromEvents = fromEvents)
}
