package com.specificlanguages.mops.cli

/**
 * The concept name as shown in a text column: the bare short name (the last dotted segment) by default, or the full
 * qualified name when [full] is set. The shortening is purely lexical here; short names are safe as the default because
 * the daemon's concept-name resolver accepts them on the way back in. JSON output keeps the qualified name
 * unconditionally and never passes through here.
 */
internal fun displayConcept(concept: String, full: Boolean): String =
    if (full) concept else concept.substringAfterLast('.')
