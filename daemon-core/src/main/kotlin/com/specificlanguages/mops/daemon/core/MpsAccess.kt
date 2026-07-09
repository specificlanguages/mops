package com.specificlanguages.mops.daemon.core

interface MpsAccess {
    fun <T> read(block: MpsRead.() -> T): T

    fun <T> write(block: MpsWrite.() -> T): T

    /**
     * Runs [block] against the [MpsMake] operations. Unlike [read] and [write] this does *not* wrap the block in an MPS
     * model action: the make framework manages its own model access, and running it inside a read/write action would
     * deadlock. Resource collection inside a make operation takes its own short read actions.
     */
    fun <T> make(block: MpsMake.() -> T): T
}
