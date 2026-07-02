package com.specificlanguages.mops.daemon.core

sealed interface MpsResult<out T> {
    data class Ok<T>(val value: T) : MpsResult<T>

    data class Error(val code: MpsErrorCode, val message: String) : MpsResult<Nothing>

    data class ProtocolError(val code: String, val message: String) : MpsResult<Nothing>
}
