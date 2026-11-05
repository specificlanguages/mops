package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.impl.local.FileWatcher
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists

/**
 * Makes sure the native file watcher has reported every change made before [pass] was called. There is no API to
 * flush pending watcher events, and on macOS an event arrives up to about 300 ms after the change. `fsnotifier` reports
 * events in order, so once it reports a change made by [pass], it has reported every earlier change too.
 *
 * Each pass creates a fresh directory with a file in it under [root], a folder that is watched recursively, and waits
 * until the watcher reports a path inside that directory. A fresh directory can only be named by events that happened
 * after it was created. The raw watcher events are observed through `FileWatcher.myTestNotifier`, a private hook that
 * the platform calls for every event; see the mps-api-research note native-file-watcher-in-headless-mps.md.
 */
internal class FileWatcherFence private constructor(private val root: Path) {
    private val pending = ConcurrentHashMap<String, CountDownLatch>()

    /** Returns `true` once the watcher reported the fence, or `false` after [timeout] or when it cannot be written. */
    fun pass(timeout: Duration): Boolean {
        val marker = root.resolve(UUID.randomUUID().toString())
        val reported = CountDownLatch(1)
        pending[marker.toString()] = reported
        try {
            Files.createDirectory(marker)
            Files.createFile(marker.resolve("fence"))
            return reported.await(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: IOException) {
            return false
        } finally {
            pending.remove(marker.toString())
            deleteQuietly(marker)
        }
    }

    private fun onEvent(path: String) {
        pending.forEach { (marker, reported) ->
            if (path == marker || path.startsWith(marker + File.separator)) reported.countDown()
        }
    }

    private fun deleteQuietly(marker: Path) {
        try {
            marker.resolve("fence").deleteIfExists()
            marker.deleteIfExists()
        } catch (_: IOException) {
            // A leftover fence directory is harmless: every pass uses a new one.
        }
    }

    companion object {
        /** The fence of this JVM, or `null` when the watcher's event hook is not available. */
        val instance: FileWatcherFence? by lazy { create() }

        private fun create(): FileWatcherFence? {
            val fileWatcher = (LocalFileSystem.getInstance() as? LocalFileSystemImpl)?.fileWatcher ?: return null
            val hook = try {
                FileWatcher::class.java.getDeclaredField("myTestNotifier").apply { isAccessible = true }
            } catch (_: ReflectiveOperationException) {
                return null
            }
            if (hook.get(fileWatcher) != null) return null
            // Reported paths are canonical and system-dependent, so the root is too.
            val root = try {
                Path.of(PathManager.getSystemPath(), "mops-file-watcher-fence").createDirectories().toRealPath()
            } catch (_: IOException) {
                return null
            }
            root.toFile().listFiles()?.forEach { FileUtil.delete(it) }
            LocalFileSystem.getInstance().addRootToWatch(FileUtil.toSystemIndependentName(root.toString()), true)
            val fence = FileWatcherFence(root)
            hook.set(fileWatcher, Consumer<String>(fence::onEvent))
            return fence
        }
    }
}
