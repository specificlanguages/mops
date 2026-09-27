package com.specificlanguages.mops.daemon

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.win32.StdCallLibrary

internal object DaemonProcess {
    /** Detach from the launching session or console before publishing readiness. */
    fun detach() {
        if (Platform.isWindows()) {
            val kernel = Native.load("kernel32", WindowsConsole::class.java)
            if (!kernel.FreeConsole()) {
                val error = Native.getLastError()
                check(error == ERROR_INVALID_PARAMETER) { "Could not detach daemon console: Windows error $error" }
            }
        } else {
            val libc = Native.load(Platform.C_LIBRARY_NAME, UnixSession::class.java)
            if (libc.getsid(0).toLong() == ProcessHandle.current().pid()) return
            check(libc.setsid() != -1) { "Could not detach daemon session: errno ${Native.getLastError()}" }
        }
    }

    private const val ERROR_INVALID_PARAMETER = 87

    private interface UnixSession : Library {
        fun getsid(pid: Int): Int
        fun setsid(): Int
    }

    private interface WindowsConsole : StdCallLibrary {
        fun FreeConsole(): Boolean
    }
}
