package com.specificlanguages.mops.protocol

/** Returns the Levenshtein edit distance between two strings. */
fun levenshteinDistance(a: String, b: String): Int {
    val previous = IntArray(b.length + 1) { it }
    val current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        for (k in previous.indices) previous[k] = current[k]
    }
    return previous[b.length]
}
