package com.specificlanguages.mops.daemon

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile

/**
 * Prepares the native file watcher's executable (`fsnotifier`) before MPS starts. The generic MPS distribution archive
 * does not keep the executable bit, so the distribution's binary is copied into a directory of the daemon, made
 * executable, and passed to the platform through `idea.filewatcher.executable.path`. Without a binary for this
 * platform the watcher is turned off through `idea.filewatcher.disabled`, and refreshes walk folders instead.
 *
 * Does nothing when either property is already set, so `-Didea.filewatcher.disabled=true` in the daemon's Java options
 * turns the watcher off.
 */
internal object FileWatcherExecutable {
    private const val EXECUTABLE_PROPERTY = "idea.filewatcher.executable.path"
    private const val DISABLED_PROPERTY = "idea.filewatcher.disabled"

    fun install(mpsHome: Path, directory: Path, logger: DaemonLogger) {
        if (System.getProperty(EXECUTABLE_PROPERTY) != null || System.getProperty(DISABLED_PROPERTY) != null) return
        val bundled = bundled(mpsHome)
        if (bundled == null) {
            logger.log("no native file watcher in $mpsHome for this platform; refreshes walk folders")
            System.setProperty(DISABLED_PROPERTY, "true")
            return
        }
        try {
            System.setProperty(EXECUTABLE_PROPERTY, copy(bundled, directory).toString())
        } catch (exception: IOException) {
            logger.log("cannot prepare the native file watcher $bundled: $exception; refreshes walk folders")
            System.setProperty(DISABLED_PROPERTY, "true")
        }
    }

    private fun copy(bundled: Path, directory: Path): Path {
        val target = directory.createDirectories().resolve(bundled.fileName)
        if (!target.isRegularFile() || Files.mismatch(bundled, target) != -1L) {
            val temporary = Files.createTempFile(directory, bundled.fileName.toString(), ".tmp")
            Files.copy(bundled, temporary, StandardCopyOption.REPLACE_EXISTING)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        if (!Files.isExecutable(target) && !target.toFile().setExecutable(true)) {
            throw IOException("cannot make $target executable")
        }
        return target
    }

    /** The distribution's `fsnotifier` for the current operating system and architecture, if it has one. */
    private fun bundled(mpsHome: Path): Path? {
        val os = System.getProperty("os.name")
        val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
        val relative = when {
            os.startsWith("Mac") -> "bin/mac/fsnotifier"
            os.startsWith("Windows") -> "bin/win/fsnotifier.exe"
            // Before MPS 2026.3 the distribution has only an x86-64 binary for Linux.
            os.startsWith("Linux") && arm -> "bin/linux-aarch64/fsnotifier"
            os.startsWith("Linux") -> "bin/linux/fsnotifier"
            else -> return null
        }
        return mpsHome.resolve(relative).takeIf { it.isRegularFile() }
    }
}
