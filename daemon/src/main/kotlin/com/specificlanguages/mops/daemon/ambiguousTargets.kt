/**
 * Logic. Hides: the text of an ambiguous-target error, shared by module targets and every kind of Navigation Target.
 */
package com.specificlanguages.mops.daemon

/**
 * Reports that [target] names more than one [kind] (`module`, `model`, ...), or more than one object of mixed kinds
 * when [kind] is null; each of [rows] names one candidate.
 */
internal fun ambiguousTargetMessage(kind: String?, target: String, rows: List<String>): String =
    "ambiguous ${kind?.let { "$it " }.orEmpty()}target $target:\n" + rows.joinToString("\n")
