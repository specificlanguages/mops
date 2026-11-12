package com.specificlanguages.mops.cli.output

private const val ESC = '\u001B'

/**
 * One column of text output: [text], shown in a terminal as an OSC 8 hyperlink to [link] when [link] is set.
 */
internal data class TextColumn(val text: String, val link: String? = null) {
    /**
     * Renders [text] passed through [escape], wrapped in the hyperlink markup when [link] is set. The markup and [link]
     * never go through [escape], so escaping the text cannot break the link.
     */
    fun render(escape: (String) -> String = { it }): String {
        val shown = escape(text)
        return if (link == null) shown else "$ESC]8;;$link$ESC\\$shown$ESC]8;;$ESC\\"
    }
}
