package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.MakeResponse

/**
 * Make (generation and compilation) operations on the MPS project.
 *
 * Unlike [MpsRead] and [MpsWrite], make operations run *outside* an MPS model read/write action — the MPS make
 * framework acquires its own model locks — so they are reached through [MpsAccess.make], not [MpsAccess.read] /
 * [MpsAccess.write]. Operations throw [MpsRequestException] on failures that carry a specific error code (e.g. a
 * requested module that cannot be resolved); a make that runs but reports generation errors is a normal result with
 * [com.specificlanguages.mops.protocol.MakeOutcome.FAILED], not an exception.
 */
interface MpsMake {
    /**
     * Makes the modules named by [modules] (each a module name or serialized module reference) together with their
     * transitive dependency closure, so any un-made dependency — direct or indirect — is made too. Fails with
     * [MpsRequestException] if a name resolves to no project module or to more than one.
     */
    fun makeModules(modules: List<String>): MakeResponse

    /**
     * Makes every generatable module in the project.
     */
    fun makeProject(): MakeResponse
}
