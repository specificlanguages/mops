package com.specificlanguages.mops.cli

/**
 * Raised when `mops explain` is given a topic path that does not resolve to an embedded page.
 */
class UnknownTopicException(message: String) : RuntimeException(message)
