package com.specificlanguages.mops.daemon

import java.nio.file.Path

/**
 * An IntelliJ system directory that one test run uses; [diagnostic] is set when it is a fallback for a busy persistent
 * directory. Closing it runs [release].
 */
class SystemDirectory(val path: Path, val diagnostic: String?, private val release: () -> Unit = {}) : AutoCloseable {
    override fun close() = release()
}
