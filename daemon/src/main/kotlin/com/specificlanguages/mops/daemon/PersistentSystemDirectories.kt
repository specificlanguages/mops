package com.specificlanguages.mops.daemon

import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories

/**
 * IntelliJ system directories (VFS, indexes, caches) that test runs keep between runs, so that the processes of a run do
 * not scan and index the whole MPS distribution again each time.
 *
 * [cache] holds one directory per role and MPS build, so a run never reuses the indexes of another build. One run at a
 * time uses a role's directory: [acquire] takes an exclusive file lock, which also excludes runs in other daemons. When
 * the directory is busy, the run gets its own fresh fallback directory and a diagnostic that says so.
 */
class PersistentSystemDirectories(cache: Path, mpsBuild: String) {
    private val root = cache.toAbsolutePath().normalize().resolve(mpsBuild)

    /**
     * Locks the persistent directory of [role] until the returned directory is closed. When another run holds it,
     * returns [fallback] instead, with a diagnostic naming the [process] that uses it.
     */
    fun acquire(role: String, process: String, fallback: Path): SystemDirectory {
        val lockFile = root.resolve("$role.lock")
        // Closing any channel of a file releases all locks this JVM holds on it, so a second run in this JVM must not
        // open the lock file while the first holds it.
        if (heldInThisProcess.add(lockFile)) {
            root.createDirectories()
            val channel = FileChannel.open(lockFile, CREATE, WRITE)
            if (channel.tryLock() != null) {
                return SystemDirectory(root.resolve(role), null) {
                    channel.close()
                    heldInThisProcess.remove(lockFile)
                }
            }
            channel.close()
            heldInThisProcess.remove(lockFile)
        }
        return SystemDirectory(fallback, "The $process's system directory ${root.resolve(role)} is in use by another " +
            "test run; this run uses the fresh directory $fallback and indexes the MPS distribution again.")
    }

    private companion object {
        val heldInThisProcess: MutableSet<Path> = ConcurrentHashMap.newKeySet()
    }
}
