package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import jetbrains.mps.project.Project

internal object CodeModeProjectProvider {
    @Volatile private var state: State? = null

    fun initialize(project: Project, access: MpsAccess) {
        check(state == null) { "Code Mode project provider is already initialized" }
        state = State(project, access)
    }

    fun clear(project: Project) {
        check(state?.project === project) { "Code Mode project provider belongs to a different project" }
        state = null
    }

    fun project(): Project = state?.project ?: error("Code Mode project is unavailable outside the daemon project lifecycle")
    fun access(): MpsAccess = state?.access ?: error("Code Mode access is unavailable outside the daemon project lifecycle")

    private data class State(val project: Project, val access: MpsAccess)
}
