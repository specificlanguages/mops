package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * Contact information and metadata of a running mops daemon.
 */
@Serializable
data class DaemonRecord(
    // Contact information
    val port: Int,
    val token: String,
    val pid: Long,

    // Compatibility
    val daemonVersion: String,

    // Context
    val context: DaemonContext,

    // Runtime information
    @Serializable(with = PathAsStringSerializer::class)
    val workspace: Path,
    val startupTime: String,
)
