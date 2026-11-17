package com.specificlanguages.mops.daemon.filewatcher

import java.nio.file.Path

/**
 * Value. Where the native file watcher reports changes in order with the fence at [fenceRoot]. [recursiveRoots] and
 * [fenceRoot] are real paths. Without [orderedAcrossDrives], only changes on the fence's drive are in order with it.
 */
internal data class WatchCoverage(
    val recursiveRoots: List<Path>,
    val fenceRoot: Path,
    val orderedAcrossDrives: Boolean,
) {
    /** Whether a change under [realPath] is reported before the fence that follows it. */
    fun covers(realPath: Path): Boolean =
        (orderedAcrossDrives || realPath.root == fenceRoot.root) && recursiveRoots.any(realPath::startsWith)
}
