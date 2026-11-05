package com.specificlanguages.mops.cli.output

/**
 * Renders one row of row-shaped text output: [columns] joined with tabs. A backslash, newline, carriage return or tab
 * inside a column is written as the two-character escape `\\`, `\n`, `\r` or `\t`, so a column cannot end the row or
 * add a column, and the original text can be recovered.
 */
internal fun textRow(columns: List<String>): String = columns.joinToString("\t") { escapeColumn(it) }

private fun escapeColumn(value: String): String =
    value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
