package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

interface DaemonPool {
    fun ensureDaemon(context: DaemonContext): DaemonClient

    sealed class Spec {
        object All : Spec()
        class ForProject(val projectPath: Path) : Spec()
    }

    fun findRecords(spec: Spec): List<StoredDaemonRecord>

    fun stop(record: StoredDaemonRecord): Boolean
}

class DefaultDaemonPool(
    private val records: DaemonRecordStore,
    private val launcher: DaemonLauncher
) : DaemonPool {
    override fun ensureDaemon(context: DaemonContext): DaemonClient {
        val existing = records.read(context.realProjectPath)
        if (existing != null) {
            val client = launcher.connectToExistingDaemon(existing.record)
            val existingResponse = try {
                client.ping()
            } catch (e: Exception) {
                System.err.println("Received exception pinging an existing daemon, deleting the daemon record")
                e.printStackTrace()
                records.deleteRecord(existing.recordPath)
                null
            }
            if (existingResponse != null) {
                if (existing.record.context != context) {
                    throw IllegalStateException(
                        "project is already owned by a mops daemon with a different context:\n" +
                                "  requested: ${context}\n" +
                                "  existing:  ${existing.record.context}",
                    )
                }
                return client
            }
        }

        val result = launcher.startDaemon(context)

        records.checkRecordWasWrittenForProject(context.realProjectPath)

        return result
    }


    override fun findRecords(spec: DaemonPool.Spec): List<StoredDaemonRecord> = when (spec) {
        is DaemonPool.Spec.All -> records.readAll()
        is DaemonPool.Spec.ForProject -> listOfNotNull(records.read(spec.projectPath))
    }

    override fun stop(record: StoredDaemonRecord): Boolean {
        records.deleteRecord(record.recordPath)
        try {
            DefaultDaemonClient.fromRecord(record.record).stop()
            waitForExit(record.record)
            return true
        } catch (e: Exception) {
            System.err.println("Exception stopping daemon for project: ${record.record.context.realProjectPath}")
            e.printStackTrace()
            return false
        }
    }

    private fun waitForExit(record: DaemonRecord) {
        val handle = ProcessHandle.of(record.pid).orElse(null) ?: return
        try {
            handle.onExit().get(5, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            System.err.println("Timed out waiting for daemon pid=${record.pid} to exit after stop request")
        } catch (e: Exception) {
            System.err.println("Unexpected error waiting for daemon pid=${record.pid} to exit after stop request")
            e.printStackTrace()
        }
    }

    companion object {
        fun forDaemonHome(path: Path): DefaultDaemonPool {
            val store = DaemonRecordStore.forDaemonHome(path)
            return DefaultDaemonPool(store, FullClasspathDaemonLauncher(store))
        }
    }
}
