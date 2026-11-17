package com.specificlanguages.mops.daemon.filewatcher

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.local.PluggableFileWatcher
import java.nio.file.Path
import java.time.Duration

/**
 * Adapter. Answers [assessFileWatcher] from the platform's file watcher, the plugin's `HeadlessNativeFileWatcher`, and
 * the [FileWatcherFence]. Must run outside the EDT.
 *
 * Hides: how the daemon reads the state of the native file watcher.
 *
 * Breaks `connascence-in-types`: the plugin class loader loads `HeadlessNativeFileWatcher`, so the daemon cannot share
 * a type with it and calls it by its class name and method names (`changeCount`, `recursiveRoots`), as `JavaParser`
 * calls the plugin's `JavaParserBridge`.
 */
internal class NativeFileWatcherProbe private constructor(private val headless: PluggableFileWatcher) : FileWatcherSignals {
    private val fileWatcher = (LocalFileSystem.getInstance() as LocalFileSystemImpl).fileWatcher

    // Creating the fence adds a watch root, so it is created before waiting for the roots to settle.
    private val fence = if (fileWatcher.isOperational) FileWatcherFence.instance else null

    override fun isOperational(): Boolean = fileWatcher.isOperational

    override fun awaitSettledRoots(): Boolean {
        val deadline = System.nanoTime() + ROOTS_TIMEOUT.toNanos()
        while (fileWatcher.isSettingRoots) {
            if (System.nanoTime() > deadline) return false
            Thread.sleep(POLL_MILLIS)
        }
        return true
    }

    override fun awaitFence(): FileWatcherSignals.Fence = when {
        fence == null -> FileWatcherSignals.Fence.Unavailable
        fence.awaitReported(FENCE_TIMEOUT) -> FileWatcherSignals.Fence.Reported(coverage(fence))
        else -> FileWatcherSignals.Fence.NotReported
    }

    override fun changeCount(): Long? {
        // The watcher reports failures as notifications that are delivered on the EDT.
        ApplicationManager.getApplication().invokeAndWait {}
        return headless.javaClass.getMethod("changeCount").invoke(headless) as Long?
    }

    private fun coverage(fence: FileWatcherFence) = WatchCoverage(
        recursiveRoots = (headless.javaClass.getMethod("recursiveRoots").invoke(headless) as List<*>)
            .map { Path.of(it as String) },
        fenceRoot = fence.root,
        // On Windows, fsnotifier watches each drive in its own thread, so it keeps the order of events only within a drive.
        orderedAcrossDrives = !System.getProperty("os.name").startsWith("Windows"),
    )

    companion object {
        private const val CLASS_NAME = "com.specificlanguages.mops.daemon.plugin.HeadlessNativeFileWatcher"
        private val FENCE_TIMEOUT: Duration = Duration.ofSeconds(2)
        private val ROOTS_TIMEOUT: Duration = Duration.ofSeconds(10)
        private const val POLL_MILLIS = 10L

        /** The probe of the plugin's watcher, or `null` when the plugin did not register it. */
        fun find(): NativeFileWatcherProbe? =
            PluggableFileWatcher.EP_NAME.extensionList.firstOrNull { it.javaClass.name == CLASS_NAME }
                ?.let(::NativeFileWatcherProbe)
    }
}
