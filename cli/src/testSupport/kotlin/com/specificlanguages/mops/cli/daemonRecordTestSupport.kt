package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import java.nio.file.Path

/** The daemon record this store holds for the project at [projectPath], or null if it holds none. */
fun DaemonRecordStore.recordOf(projectPath: Path): StoredDaemonRecord? = read(projectPath)
