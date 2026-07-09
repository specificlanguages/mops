package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsMake
import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.daemon.core.MpsWrite

/**
 * An [MpsAccess] that runs read and write blocks against [operations] and make blocks against [make], so tests can stub
 * and verify the individual operations with Mockito instead of booting MPS. The block passthrough is all the seam does;
 * routing writes through `write` is enforced by [MpsRead] not exposing the write operations. [make] defaults to a stub
 * that fails if a test reaches make without providing one.
 */
fun mpsAccessOver(operations: MpsWrite, make: MpsMake = UnstubbedMake): MpsAccess = object : MpsAccess {
    override fun <T> read(block: MpsRead.() -> T): T = operations.block()

    override fun <T> write(block: MpsWrite.() -> T): T = operations.block()

    override fun <T> make(block: MpsMake.() -> T): T = make.block()
}

private object UnstubbedMake : MpsMake {
    override fun makeModules(modules: List<String>) = error("MpsMake.makeModules not stubbed in this test")
    override fun makeProject() = error("MpsMake.makeProject not stubbed in this test")
}
