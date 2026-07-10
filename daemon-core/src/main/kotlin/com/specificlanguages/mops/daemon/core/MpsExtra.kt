package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.NodeTarget

/**
 * Operations that must run *outside* an MPS model read/write action, reached through [MpsAccess.extra] rather than
 * [MpsAccess.read] / [MpsAccess.write]. Each manages its own model access — make acquires the make framework's own model
 * locks; rendering bridges to the EDT and blocks on it — so running them inside a read or write action would deadlock.
 *
 * Operations throw [MpsRequestException] on failures that carry a specific error code (e.g. an unresolved make target or
 * render node). A make that runs but reports generation errors is a normal result with
 * [com.specificlanguages.mops.protocol.MakeOutcome.FAILED], not an exception.
 */
interface MpsExtra {
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

    /**
     * Renders one resolved node as the plain text of its default editor, the way it would appear in the MPS editor. The
     * returned text preserves the editor's line breaks and indentation.
     *
     * Fails with [MpsErrorCode.LANGUAGE_NOT_LOADED] when any concept in the node's subtree did not resolve because its
     * language is not made, unless [allowReflective] is set, which renders anyway. A resolved concept that simply
     * defines no editor is not an error: MPS renders it with its generic reflective editor.
     */
    fun renderNode(target: NodeTarget, allowReflective: Boolean = false): String
}
