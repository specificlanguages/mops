package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import jetbrains.mps.project.MPSProject

class Mops(
    val project: MPSProject,
    val access: MpsAccess,
) {
    val parsing = MopsParsing(this)
    val search = MopsSearch(this)
}
