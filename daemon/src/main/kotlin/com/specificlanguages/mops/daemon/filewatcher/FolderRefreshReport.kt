package com.specificlanguages.mops.daemon.filewatcher

import java.time.Duration

/**
 * Value. How one refresh refreshed folders and what it cost. [fullWalkReason] is `null` when the refresh relied on the
 * native file watcher.
 */
internal data class FolderRefreshReport(
    val fullWalkReason: FullWalkReason?,
    val watcherWait: Duration,
    val fromEvents: Int = 0,
    val walked: Int = 0,
    val refreshTime: Duration = Duration.ZERO,
) {
    /** Adds the folders of [plan], refreshed in [time]. */
    fun withFolders(plan: FolderRefreshPlan, time: Duration): FolderRefreshReport = copy(
        fromEvents = fromEvents + plan.fromEvents.size,
        walked = walked + plan.walk.size,
        refreshTime = refreshTime + time,
    )

    /** The daemon log line of this refresh. */
    fun logLine(): String {
        val mode = fullWalkReason?.let { "a full walk because ${it.description}" } ?: "file watcher events"
        return "refreshed external changes using $mode: $fromEvents folders from watcher events, $walked walked; " +
            "watcher wait ${watcherWait.toMillis()} ms, refresh ${refreshTime.toMillis()} ms"
    }
}
