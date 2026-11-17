package com.specificlanguages.mops.daemon.filewatcher

/** Why a refresh walks every folder instead of relying on the native file watcher's events. */
internal enum class FullWalkReason(val description: String) {
    NOT_REGISTERED("the mops file watcher is not registered"),
    NOT_OPERATIONAL("the file watcher is not operational"),
    ROOTS_NOT_SETTLED("the file watcher is still setting its roots"),
    FAILURE_REPORTED("the file watcher reported a failure or its restarts cannot be counted"),
    NO_EVENT_HOOK("the file watcher event hook is not available"),
    FENCE_NOT_REPORTED("the file watcher did not report the fence in time"),
    FIRST_REFRESH("this is the first refresh"),
    ROOTS_CHANGED("the watch roots changed or the file watcher restarted"),
}
