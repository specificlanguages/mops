package com.specificlanguages.mops.daemon.plugin

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.impl.local.NativeFileWatcherImpl
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The platform's native file watcher (`fsnotifier`), enabled in a headless application. The platform registers its own
 * instance, which stays off in command-line and test applications; this second registration differs only in that
 * check, and can still be turned off with `-Didea.filewatcher.disabled=true`.
 *
 * The daemon reads [changeCount] and [recursiveRoots] by reflection, because this class is loaded by the plugin class
 * loader.
 */
class HeadlessNativeFileWatcher : NativeFileWatcherImpl() {
    private val rootChanges = AtomicLong()

    @Volatile
    private var recursive: List<String> = emptyList()

    @Volatile
    private var flat: List<String> = emptyList()

    @Volatile
    private var failed = false

    private val startAttempts: AtomicInteger? = startAttemptCounter()

    init {
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == FAILURE_GROUP) failed = true
                }
            },
        )
    }

    override fun isDisabled(): Boolean = java.lang.Boolean.getBoolean(DISABLED_PROPERTY)

    override fun setWatchRoots(recursive: List<String>, flat: List<String>, shuttingDown: Boolean) {
        if (recursive != this.recursive || flat != this.flat) {
            this.recursive = recursive
            this.flat = flat
            rootChanges.incrementAndGet()
        }
        super.setWatchRoots(recursive, flat, shuttingDown)
    }

    /**
     * A number that changes whenever the watcher may have missed changes: when the watch roots change and when the
     * `fsnotifier` process is started again. `null` once the watcher has reported a failure, such as the Linux inotify
     * watch limit, or when the start count cannot be read.
     */
    fun changeCount(): Long? {
        val starts = startAttempts ?: return null
        return if (failed) null else rootChanges.get() + starts.get()
    }

    /** The recursive watch roots as last set, canonical and system-dependent. */
    fun recursiveRoots(): List<String> = recursive

    /**
     * The platform restarts a terminated `fsnotifier` without reporting anything as changed, but counts each start in a
     * private field.
     */
    private fun startAttemptCounter(): AtomicInteger? =
        try {
            NativeFileWatcherImpl::class.java.getDeclaredField("myStartAttemptCount")
                .apply { isAccessible = true }
                .get(this) as? AtomicInteger
        } catch (_: ReflectiveOperationException) {
            null
        }

    private companion object {
        const val DISABLED_PROPERTY = "idea.filewatcher.disabled"
        const val FAILURE_GROUP = "File Watcher Messages"
    }
}
