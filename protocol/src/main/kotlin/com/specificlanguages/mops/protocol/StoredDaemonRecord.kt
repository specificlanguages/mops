package com.specificlanguages.mops.protocol

import java.nio.file.Path

data class StoredDaemonRecord(val recordPath: Path, val record: DaemonRecord)