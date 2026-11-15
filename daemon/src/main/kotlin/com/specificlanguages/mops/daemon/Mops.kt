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
    val runtime = MopsRuntime(this)

    /** Notes on what mops did on the program's behalf, returned with the program's result. */
    internal val notes = mutableListOf<String>()
}

/**
 * Value. The Code Mode path of each namespace object a program reaches from `mops`, keyed by the namespace's class.
 *
 * Hides: the mapping between namespace classes and the paths that help, access annotations, and the Code Mode
 * Reference print.
 */
internal val codeModeNamespacePaths: Map<Class<*>, String> = mapOf(
    Mops::class.java to "mops",
    MopsEditing::class.java to "mops.editing",
    MopsEditingBuild::class.java to "mops.editing.build",
    MopsParsing::class.java to "mops.parsing",
    JavaSnippetParser::class.java to "mops.parsing.java",
    MopsSearch::class.java to "mops.search",
    MopsLookup::class.java to "mops.lookup",
    MopsRuntime::class.java to "mops.runtime",
    MopsTesting::class.java to "mops.testing",
)
