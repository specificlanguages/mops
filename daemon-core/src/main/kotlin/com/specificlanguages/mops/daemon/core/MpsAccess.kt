package com.specificlanguages.mops.daemon.core

interface MpsAccess {
    fun <T> read(block: MpsRead.() -> T): T

    fun <T> write(block: MpsWrite.() -> T): T

    /**
     * Runs [block] against the [MpsExtra] operations, which must run with no read/write action active. This method
     * *requires* that no read/write action is active. Each extra operation manages its own model access.
     */
    fun <T> extra(block: MpsExtra.() -> T): T
}
