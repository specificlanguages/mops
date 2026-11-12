package com.specificlanguages.mops.cli.output

/**
 * Renders one row of row-shaped text output: [columns] joined with tabs. A backslash, newline, carriage return or tab
 * inside a column's text is written as the two-character escape `\\`, `\n`, `\r` or `\t`, so a column cannot end the row
 * or add a column, and the original text can be recovered. A column's hyperlink markup is written as is.
 */
internal fun textRow(columns: List<TextColumn>): String = columns.joinToString("\t") { it.render(::escapeColumn) }

/** Renders one row of plain [columns], as [textRow] does for columns without hyperlinks. */
@JvmName("textRowOfStrings")
internal fun textRow(columns: List<String>): String = textRow(columns.map { TextColumn(it) })

private fun escapeColumn(value: String): String =
    value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
