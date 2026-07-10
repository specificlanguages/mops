package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsExtra
import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.daemon.core.MpsWrite
import com.specificlanguages.mops.protocol.NodeTarget

/**
 * An [MpsAccess] that runs read and write blocks against [operations] and extra blocks against [extra], so tests
 * can stub and verify the individual operations with Mockito instead of booting MPS. The block passthrough is all the
 * seam does; routing writes through `write` is enforced by [MpsRead] not exposing the write operations. [extra]
 * defaults to a stub that fails if a test reaches make/render without providing one.
 */
fun mpsAccessOver(operations: MpsWrite, extra: MpsExtra = UnstubbedExtra): MpsAccess = object : MpsAccess {
    override fun <T> read(block: MpsRead.() -> T): T = operations.block()

    override fun <T> write(block: MpsWrite.() -> T): T = operations.block()

    override fun <T> extra(block: MpsExtra.() -> T): T = extra.block()
}

private object UnstubbedExtra : MpsExtra {
    override fun makeModules(modules: List<String>) = error("MpsExtra.makeModules not stubbed in this test")
    override fun makeProject() = error("MpsExtra.makeProject not stubbed in this test")
    override fun renderNode(target: NodeTarget, allowReflective: Boolean) =
        error("MpsExtra.renderNode not stubbed in this test")
}
