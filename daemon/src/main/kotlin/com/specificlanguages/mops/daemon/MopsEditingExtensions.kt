package com.specificlanguages.mops.daemon

object MopsEditingExtensions {
    /** Returns editing operations for MPS build projects. */
    @CodeModeExtension(MpsAccessLevel.NONE)
    @JvmStatic
    fun getBuild(editing: MopsEditing): MopsEditingBuild {
        requireOwner(editing.mops)
        return MopsEditingBuild(editing.mops)
    }
}
