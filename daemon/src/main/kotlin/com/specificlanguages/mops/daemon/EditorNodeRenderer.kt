package com.specificlanguages.mops.daemon

import jetbrains.mps.editor.runtime.HeadlessEditorComponent
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.project.Project
import org.jetbrains.mps.openapi.model.SNode

/**
 * Renders a node's default editor as plain text, headlessly.
 *
 * [HeadlessEditorComponent] builds the node's editor cell tree without any UI; `renderText()` then serializes that tree
 * with the editor's own whitespace and indentation, driven by cell styles rather than geometry. The node need not be a
 * Root Node, but must be registered in a model of the project's repository. See docs/mps/editor-cell-rendering.md.
 *
 * Threading: this runs synchronously on the calling thread and requires the EDT, with read access, already in effect —
 * `EditorComponent.dispose()` asserts the EDT (via `NodeHighlightManager.dispose`) and the cell build needs a read. The
 * caller owns that bridging: it must invoke this inside a read action on the EDT (e.g. `ModelAccess.runReadInEDT`).
 * Keeping the bridging in the caller lets node resolution and rendering share one read action, and lets the caller do
 * its blocking wait for the EDT with no outer read lock held — holding one would deadlock the EDT's own write actions
 * (module reload after a make, indexing).
 */
class EditorNodeRenderer {
    fun render(node: SNode, project: Project): String {
        require(ThreadUtils.isInEDT()) {
            "render must run on the EDT: the editor's disposal asserts it. Call it inside a read action on the EDT."
        }
        val editor = HeadlessEditorComponent(project.repository)
        return try {
            editor.editNode(node)
            editor.rootCell.renderText().text
        } finally {
            editor.dispose()
        }
    }
}
