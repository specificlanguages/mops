package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import jetbrains.mps.core.platform.Platform
import jetbrains.mps.project.MPSProject

class Mops(
    val project: MPSProject,
    val access: MpsAccess,
    internal val platform: Platform,
) {
    val editing = MopsEditing(this)
    val parsing = MopsParsing(this)
    val search = MopsSearch(this)
}
