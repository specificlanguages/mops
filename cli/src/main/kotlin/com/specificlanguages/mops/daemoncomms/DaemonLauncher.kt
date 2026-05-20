package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecord

/**
 * A launcher for the remote daemon process.
 *
 */
interface DaemonLauncher {
    fun startDaemon(context: DaemonContext): DaemonClient
    fun connectToExistingDaemon(record: DaemonRecord): DaemonClient
}
