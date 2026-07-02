package com.specificlanguages.mops.daemon.core

interface MpsAccess {
    fun <T> read(block: MpsRead.() -> T): T

    fun <T> write(block: MpsWrite.() -> T): T
}
