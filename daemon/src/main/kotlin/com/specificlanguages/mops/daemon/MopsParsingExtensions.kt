package com.specificlanguages.mops.daemon

object MopsParsingExtensions {
    /** Returns the Java snippet parser. Its insertion methods require command access and support Java 8 syntax. */
    @CodeModeExtension(MpsAccessLevel.NONE)
    @JvmStatic
    fun getJava(parsing: MopsParsing): JavaSnippetParser {
        requireOwner(parsing.mops)
        return JavaSnippetParser()
    }
}
