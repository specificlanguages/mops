package com.specificlanguages.mops.daemon

/**
 * Base of the live node accessors that shadow a native no-argument `SNode` getter in Groovy. Iterating an accessor
 * yields what the shadowed getter yields; any member other than indexing and iteration fails with a message naming the
 * indexed form, iteration, and the native alternative.
 */
abstract class NodeAccessor<T>(
    private val accessor: String,
    private val key: String,
    private val nativeAlternative: String,
) : Iterable<T> {
    fun propertyMissing(name: String): Nothing = missingMember(name)
    fun propertyMissing(name: String, @Suppress("unused") value: Any?): Nothing = missingMember(name)
    fun methodMissing(name: String, @Suppress("unused") arguments: Any?): Nothing = missingMember("$name()")

    private fun missingMember(member: String): Nothing = throw IllegalArgumentException(
        "No such member '$member' on $accessor. Use $accessor[$key] for one entry, iterate $accessor for all entries, or use $nativeAlternative for the native collection"
    )
}
