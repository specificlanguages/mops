package com.specificlanguages.mops.protocol

/**
 * States how mops assigned command-line arguments to their roles, for an error about arguments that do not fit, for
 * example "mops read `a` as MODEL_TARGET and `b` as NODE_ID". Each reading pairs the argument text with its role.
 */
fun argumentReading(vararg readings: Pair<String, String>): String =
    "mops read " + readings.joinToString(" and ") { (argument, role) -> "`$argument` as $role" }
