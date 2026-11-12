package com.specificlanguages.mops.cli.output

/** How text output shows a node reference. */
enum class NodeReferenceFormat {
    /** The serialized reference. */
    SERIALIZED,

    /** The serialized reference, as a terminal hyperlink to the MPS node URL. */
    LINKED,

    /** The MPS node URL. */
    URL,
    ;

    /** This format without a terminal hyperlink: [LINKED] becomes [SERIALIZED]. */
    fun withoutLink(): NodeReferenceFormat = if (this == LINKED) SERIALIZED else this

    companion object {
        /** [LINKED] when mops writes to a terminal, [SERIALIZED] otherwise. */
        fun default(): NodeReferenceFormat = if (System.console() != null) LINKED else SERIALIZED
    }
}
