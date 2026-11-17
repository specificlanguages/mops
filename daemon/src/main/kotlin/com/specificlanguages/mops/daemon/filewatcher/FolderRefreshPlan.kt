package com.specificlanguages.mops.daemon.filewatcher

import java.nio.file.Path

/** Value. The folders one refresh walks, and the folders it refreshes from the native file watcher's events. */
internal data class FolderRefreshPlan(val walk: List<Path>, val fromEvents: List<Path>)
