package com.specificlanguages.mops.daemon.filewatcher

import com.specificlanguages.mops.daemon.filewatcher.FileWatcherSignals.Fence
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderRefreshPolicyTest {
    @Test
    fun `a watcher that reported the fence with an unchanged count is trusted`() {
        assertEquals(WatcherAssessment.Trusted(7, coverage), assessFileWatcher(FakeSignals(), previousChangeCount = 7))
    }

    @Test
    fun `each failed check walks every folder and keeps the change count it read`() {
        val cases = listOf(
            Triple(null, 7L, WatcherAssessment.FullWalk(FullWalkReason.NOT_REGISTERED, 7)),
            Triple(FakeSignals(operational = false), 7L, WatcherAssessment.FullWalk(FullWalkReason.NOT_OPERATIONAL, 7)),
            Triple(FakeSignals(settled = false), 7L, WatcherAssessment.FullWalk(FullWalkReason.ROOTS_NOT_SETTLED, 7)),
            Triple(FakeSignals(changeCount = null), 7L, WatcherAssessment.FullWalk(FullWalkReason.FAILURE_REPORTED, null)),
            Triple(FakeSignals(fence = Fence.Unavailable), 7L, WatcherAssessment.FullWalk(FullWalkReason.NO_EVENT_HOOK, 7)),
            Triple(
                FakeSignals(fence = Fence.NotReported), 7L, WatcherAssessment.FullWalk(FullWalkReason.FENCE_NOT_REPORTED, 7),
            ),
            Triple(FakeSignals(), null, WatcherAssessment.FullWalk(FullWalkReason.FIRST_REFRESH, 7)),
            Triple(FakeSignals(changeCount = 8), 7L, WatcherAssessment.FullWalk(FullWalkReason.ROOTS_CHANGED, 8)),
        )
        cases.forEach { (signals, previous, expected) ->
            assertEquals(expected, assessFileWatcher(signals, previous), expected.reason.name)
        }
    }

    @Test
    fun `a trusted watcher refreshes known covered folders from events and walks the others`() {
        val known = Path.of("/project/solutions/a")
        val new = Path.of("/project/solutions/b")
        val outside = Path.of("/elsewhere/c")
        val unresolved = Path.of("/project/solutions/missing")
        val plan = planFolderRefresh(
            listOf(known, new, outside, unresolved),
            WatcherAssessment.Trusted(7, coverage),
            refreshedBefore = setOf(known, outside, unresolved),
            realPath = { it.takeUnless { path -> path == unresolved } },
        )
        assertEquals(FolderRefreshPlan(walk = listOf(new, outside, unresolved), fromEvents = listOf(known)), plan)
    }

    @Test
    fun `a full walk walks every folder`() {
        val known = Path.of("/project/solutions/a")
        val plan = planFolderRefresh(
            listOf(known), WatcherAssessment.FullWalk(FullWalkReason.ROOTS_CHANGED, 8), setOf(known), { it },
        )
        assertEquals(FolderRefreshPlan(walk = listOf(known), fromEvents = emptyList()), plan)
    }

    @Test
    fun `without ordering across drives only folders on the fence's drive are covered`() {
        val perDrive = WatchCoverage(listOf(Path.of("/project")), Path.of("/system/fence"), orderedAcrossDrives = false)
        assertEquals(true, perDrive.covers(Path.of("/project/a")))
        // A relative path has no root, so it stands in for a fence on another drive.
        assertEquals(false, perDrive.copy(fenceRoot = Path.of("relative/fence")).covers(Path.of("/project/a")))
    }

    @Test
    fun `the log line names the mode and the cost`() {
        val report = FolderRefreshReport(null, Duration.ofMillis(30))
            .withFolders(FolderRefreshPlan(walk = listOf(Path.of("/a")), fromEvents = listOf(Path.of("/b"), Path.of("/c"))), Duration.ofMillis(4))
        assertEquals(
            "refreshed external changes using file watcher events: 2 folders from watcher events, 1 walked; " +
                "watcher wait 30 ms, refresh 4 ms",
            report.logLine(),
        )
        assertEquals(
            "refreshed external changes using a full walk because this is the first refresh: " +
                "0 folders from watcher events, 0 walked; watcher wait 0 ms, refresh 0 ms",
            FolderRefreshReport(FullWalkReason.FIRST_REFRESH, Duration.ZERO).logLine(),
        )
    }

    private class FakeSignals(
        private val operational: Boolean = true,
        private val settled: Boolean = true,
        private val fence: Fence = Fence.Reported(coverage),
        private val changeCount: Long? = 7,
    ) : FileWatcherSignals {
        override fun isOperational() = operational
        override fun awaitSettledRoots() = settled
        override fun awaitFence() = fence
        override fun changeCount() = changeCount
    }

    private companion object {
        val coverage = WatchCoverage(listOf(Path.of("/project"), Path.of("/library")), Path.of("/system/fence"), true)
    }
}
