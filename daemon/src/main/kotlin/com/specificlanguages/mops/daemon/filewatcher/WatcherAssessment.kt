package com.specificlanguages.mops.daemon.filewatcher

/** Whether one refresh can rely on the native file watcher's events. */
internal sealed interface WatcherAssessment {
    /** The watcher's change count, which the next refresh compares with its own. */
    val changeCount: Long?

    /** The watcher has reported every change since the previous refresh under [coverage]. */
    data class Trusted(override val changeCount: Long, val coverage: WatchCoverage) : WatcherAssessment

    /** Every folder is walked, for [reason]. */
    data class FullWalk(val reason: FullWalkReason, override val changeCount: Long?) : WatcherAssessment
}
