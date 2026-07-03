package com.specificlanguages.mops.daemon.core

/**
 * A request failure with a stable error code, reported to the client as a daemon error response.
 */
class MpsRequestException(val code: MpsErrorCode, override val message: String) : RuntimeException(message)
