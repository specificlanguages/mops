package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode

/** Value. Thrown when a feature name selects no feature of a concept, or several. */
class NodeFeatureLookupException(val code: MpsErrorCode, message: String) : IllegalArgumentException(message)
