package com.specificlanguages.mops.daemon

import picocli.CommandLine

fun main(args: Array<String>) {
    var exitCode = 1
    try {
        exitCode = CommandLine(MopsDaemonCommand()).execute(*args)
    } catch (throwable: Throwable) {
        throwable.printStackTrace()
    }

    // The MPS/IntelliJ platform starts non-daemon AWT threads and holds an exclusive lock on the workspace's
    // config/system directory. Once those threads are up, a returning main never lets the JVM exit on its own, and
    // System.exit can block in platform shutdown hooks. Ending the process here unconditionally releases that
    // directory lock, so the next daemon start for the same project is not blocked by a lingering JVM.
    System.out.flush()
    System.err.flush()
    Runtime.getRuntime().halt(exitCode)
}
