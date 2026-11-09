package com.specificlanguages.mops.protocol

import java.nio.file.Path

/** The daemon record this store holds for the project at [projectPath], or null if it holds none. */
fun DaemonRecordStore.recordOf(projectPath: Path): StoredDaemonRecord? = read(projectPath)
