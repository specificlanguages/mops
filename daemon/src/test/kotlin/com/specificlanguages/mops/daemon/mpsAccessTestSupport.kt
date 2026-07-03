package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.daemon.core.MpsWrite

/**
 * An [MpsAccess] that runs read and write blocks against [operations], so tests can stub and verify the
 * individual operations with Mockito instead of booting MPS. The block passthrough is all the seam does;
 * routing writes through `write` is enforced by [MpsRead] not exposing the write operations.
 */
fun mpsAccessOver(operations: MpsWrite): MpsAccess = object : MpsAccess {
    override fun <T> read(block: MpsRead.() -> T): T = operations.block()

    override fun <T> write(block: MpsWrite.() -> T): T = operations.block()
}
