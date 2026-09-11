package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import jetbrains.mps.project.MPSProject

internal object CodeModeProjectProvider {
    @Volatile private var state: State? = null

    fun initialize(mops: Mops) {
        check(state == null) { "Code Mode project provider is already initialized" }
        state = State(mops)
    }

    fun clear(mops: Mops) {
        check(state?.mops === mops) { "Code Mode project provider belongs to a different execution" }
        state = null
    }

    fun mops(): Mops = state?.mops ?: error("Code Mode context is unavailable outside the daemon project lifecycle")
    fun project(): MPSProject = mops().project
    fun access(): MpsAccess = mops().access

    private data class State(val mops: Mops)
}
