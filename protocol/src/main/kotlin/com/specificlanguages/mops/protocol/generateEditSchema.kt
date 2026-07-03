package com.specificlanguages.mops.protocol

import java.io.File

/**
 * Build-time entry point: writes the generated edit-batch JSON Schema to the file named by the first argument. Invoked
 * by the CLI build so the schema lands in the CLI jar's resources instead of being checked in.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: generateEditSchema <output-file>" }
    val output = File(args[0])
    output.parentFile?.mkdirs()
    output.writeText(EditSchema.generate())
}
