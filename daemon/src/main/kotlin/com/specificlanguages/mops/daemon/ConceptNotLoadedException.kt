package com.specificlanguages.mops.daemon

/** Value. Thrown when a lookup needs a node's concept, but the concept did not resolve because its language is not loaded. */
class ConceptNotLoadedException(message: String) : IllegalStateException(message)
