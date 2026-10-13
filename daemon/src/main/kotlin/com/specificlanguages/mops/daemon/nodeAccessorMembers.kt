package com.specificlanguages.mops.daemon

/**
 * Rejects a member that an indexed node accessor does not have. The message points at the indexed form, iteration,
 * and the native MPS alternative, because the accessor shadows a native no-argument getter of the same name.
 */
internal fun missingAccessorMember(accessor: String, key: String, member: String, nativeAlternative: String): Nothing =
    throw IllegalArgumentException(
        "No such member '$member' on $accessor. Use $accessor[$key] for one entry, iterate $accessor for all entries, or use $nativeAlternative for the native collection"
    )
