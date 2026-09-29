package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import jetbrains.mps.core.platform.Platform
import jetbrains.mps.project.MPSProject

class Mops(
    val project: MPSProject,
    val access: MpsAccess,
    internal val platform: Platform,
    internal val testingRunner: ProjectTesting? = null,
) {
    val testing = MopsTesting(this)
    val editing = MopsEditing(this)
    val parsing = MopsParsing(this)
    val lookup = MopsLookup(this)
    val search = MopsSearch(this)
}
