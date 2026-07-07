package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

interface DaemonPool {
    fun ensureDaemon(context: DaemonContext): DaemonClient

    sealed class Spec {
        object All : Spec()
        class ForProject(val projectPath: Path) : Spec()
    }

    fun findRecords(spec: Spec): List<StoredDaemonRecord>

    fun stop(record: StoredDaemonRecord): StopOutcome

    enum class StopOutcome {
        /** A live daemon acknowledged the stop request and its process is now gone. */
        STOPPED,

        /** The recorded daemon was not running; its stale record was removed. */
        ALREADY_GONE,

        /** The daemon acknowledged the stop but its process could not be terminated, even by force. */
        NOT_TERMINATED,
    }
}

class DefaultDaemonPool(
    private val records: DaemonRecordStore,
    private val launcher: DaemonLauncher,
    private val stopGrace: Duration = Duration.ofSeconds(5),
    private val killGrace: Duration = Duration.ofSeconds(5),
) : DaemonPool {
    override fun ensureDaemon(context: DaemonContext): DaemonClient {
        val existing = records.read(context.realProjectPath)
        if (existing != null) {
            val client = launcher.connectToExistingDaemon(existing.record)
            val existingResponse = try {
                client.ping()
            } catch (e: Exception) {
                // A recorded daemon that no longer answers has simply exited or crashed. This is routine, so note it
                // in one line on stderr and start a fresh daemon, rather than dumping a stack trace the caller has to
                // filter out of otherwise-clean output.
                System.err.println(
                    "mops: recorded daemon for ${context.realProjectPath} is not responding " +
                        "(${e.message}); starting a new one",
                )
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

    override fun stop(record: StoredDaemonRecord): DaemonPool.StopOutcome {
        val acknowledged = try {
            DefaultDaemonClient.fromRecord(record.record).stop()
            true
        } catch (_: Exception) {
            // An unreachable daemon has already exited or crashed; its record is stale.
            false
        }

        if (!acknowledged) {
            records.deleteRecord(record.recordPath)
            return DaemonPool.StopOutcome.ALREADY_GONE
        }

        // A live daemon acknowledged the stop. It must actually terminate, or it keeps holding the MPS workspace
        // directory lock and blocks the next start. Force-killing only ever targets a pid we just confirmed answered
        // as our daemon, so there is no risk of killing an unrelated process that reused the pid.
        if (!awaitExitOrKill(record.record)) {
            System.err.println(
                "mops: daemon pid=${record.record.pid} did not exit even after a force-kill; leaving its record " +
                    "in place",
            )
            return DaemonPool.StopOutcome.NOT_TERMINATED
        }

        records.deleteRecord(record.recordPath)
        return DaemonPool.StopOutcome.STOPPED
    }

    /**
     * Waits for the acknowledged daemon process to exit and, if it outlives the grace period, force-kills it. Returns
     * whether the process is gone once this method returns.
     */
    private fun awaitExitOrKill(record: DaemonRecord): Boolean {
        val handle = ProcessHandle.of(record.pid).orElse(null)
            ?: return true // No such process: it has already exited.

        if (awaitExit(handle, stopGrace)) {
            return true
        }

        System.err.println("mops: daemon pid=${record.pid} did not exit after the stop request; force-killing it")
        handle.destroyForcibly()
        return awaitExit(handle, killGrace)
    }

    private fun awaitExit(handle: ProcessHandle, timeout: Duration): Boolean =
        try {
            handle.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            true
        } catch (_: TimeoutException) {
            false
        } catch (e: Exception) {
            System.err.println("Unexpected error waiting for daemon pid=${handle.pid()} to exit: ${e.message}")
            !handle.isAlive
        }

    companion object {
        fun forDaemonHome(path: Path): DefaultDaemonPool {
            val store = DaemonRecordStore.forDaemonHome(path)
            return DefaultDaemonPool(store, FullClasspathDaemonLauncher(store))
        }
    }
}
