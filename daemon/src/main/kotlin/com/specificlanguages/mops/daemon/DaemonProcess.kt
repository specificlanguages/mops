package com.specificlanguages.mops.daemon

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.win32.StdCallLibrary
import java.io.PrintStream
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.JAVA_INT

/**
 * Adapter. Detaches the daemon process from the console or process group that started it.
 *
 * Hides: which native API detaches a process on each operating system and Java version.
 */
internal object DaemonProcess {
    /**
     * Detach from the launching session or console before publishing readiness.
     *
     * Java 22 and newer use the Foreign Function API. Older JVMs use JNA from the MPS boot classpath. Starting with
     * MPS 2026.3, JNA is no longer on the boot classpath; it is in the `com.intellij.modules.jna` plugin.
     */
    fun detach() {
        val windows = System.getProperty("os.name").startsWith("Windows")
        // Each implementation is a separate class, so the JVM loads only the API that is used.
        if (Runtime.version().feature() >= FOREIGN_FUNCTION_API_JAVA_VERSION) {
            detach(System.err, "the Foreign Function API") { ForeignFunctionDetach.detach(windows) }
        } else {
            val bootLibraryPath = System.getProperty("jna.boot.library.path") ?: "(not set)"
            detach(System.err, "JNA (jna.boot.library.path=$bootLibraryPath)") { JnaDetach.detach(windows) }
        }
    }

    /**
     * Runs [detachNatively], which detaches through the native API that [api] names.
     *
     * When the native call cannot be set up, the daemon keeps running without detaching and writes a warning to
     * [warnings]. These failures are:
     * - [LinkageError]: JNA cannot load its native library or its classes.
     * - [IllegalCallerException]: native access is not enabled for the Foreign Function API.
     * - [UnsupportedOperationException]: the Foreign Function API has no native linker for this platform.
     *
     * Any other failure propagates.
     */
    internal fun detach(warnings: PrintStream, api: String, detachNatively: () -> Unit) {
        try {
            detachNatively()
        } catch (failure: LinkageError) {
            warnNotDetached(warnings, api, failure)
        } catch (failure: IllegalCallerException) {
            warnNotDetached(warnings, api, failure)
        } catch (failure: UnsupportedOperationException) {
            warnNotDetached(warnings, api, failure)
        }
    }

    private fun warnNotDetached(warnings: PrintStream, api: String, failure: Throwable) {
        warnings.println(
            "Warning: the daemon did not detach from the console or process group that started it, so closing that " +
                "console or ending that process group may stop the daemon. Detaching through $api failed: $failure"
        )
    }

    private const val FOREIGN_FUNCTION_API_JAVA_VERSION = 22
    private const val ERROR_INVALID_PARAMETER = 87

    private object ForeignFunctionDetach {
        fun detach(windows: Boolean) {
            val linker = Linker.nativeLinker()
            val stateLayout = Linker.Option.captureStateLayout()
            Arena.ofConfined().use { arena ->
                val state = arena.allocate(stateLayout)
                if (windows) {
                    val kernel = SymbolLookup.libraryLookup("kernel32", arena)
                    val freeConsole = linker.downcallHandle(
                        kernel.find("FreeConsole").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT),
                        Linker.Option.captureCallState("GetLastError"),
                    )
                    if (freeConsole.invokeWithArguments(state) as Int == 0) {
                        val error = state.get(JAVA_INT, stateLayout.byteOffset(MemoryLayout.PathElement.groupElement("GetLastError")))
                        check(error == ERROR_INVALID_PARAMETER) { "Could not detach daemon console: Windows error $error" }
                    }
                } else {
                    val libc = linker.defaultLookup()
                    val getsid = linker.downcallHandle(
                        libc.find("getsid").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT),
                    )
                    if ((getsid.invokeWithArguments(0) as Int).toLong() == ProcessHandle.current().pid()) return
                    val setsid = linker.downcallHandle(
                        libc.find("setsid").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT),
                        Linker.Option.captureCallState("errno"),
                    )
                    if (setsid.invokeWithArguments(state) as Int == -1) {
                        val errno = state.get(JAVA_INT, stateLayout.byteOffset(MemoryLayout.PathElement.groupElement("errno")))
                        error("Could not detach daemon session: errno $errno")
                    }
                }
            }
        }
    }

    private object JnaDetach {
        fun detach(windows: Boolean) {
            if (windows) {
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
    }

    private interface UnixSession : Library {
        fun getsid(pid: Int): Int
        fun setsid(): Int
    }

    private interface WindowsConsole : StdCallLibrary {
        fun FreeConsole(): Boolean
    }
}
