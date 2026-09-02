package com.yks2027.tracker

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yks2027.tracker.core.database.FocusSessionEntity
import com.yks2027.tracker.core.datastore.TimerPhase
import com.yks2027.tracker.core.datastore.TimerStateRepository
import com.yks2027.tracker.core.model.PlannerCategory
import com.yks2027.tracker.core.platform.TimerCompletionScheduler
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.feature.timer.FocusSessionSink
import com.yks2027.tracker.feature.timer.TimerController
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.0 — the TimerCompletionScheduler contract as the shared TimerController drives it,
 * against a recording fake. Both platform implementations must honour this sequence:
 * Android (alarm + FGS) and desktop (coroutine tick + tray notification).
 */
class TimerControllerContractTest {

    private class FakeScheduler : TimerCompletionScheduler {
        val calls = mutableListOf<String>()
        override fun scheduleCompletion(endAtEpochMs: Long) { calls += "schedule:$endAtEpochMs" }
        override fun cancelCompletion() { calls += "cancel" }
        override fun showCountdown(endAtEpochMs: Long) { calls += "showCountdown:$endAtEpochMs" }
        override fun showStopwatch(elapsedMs: Long) { calls += "showStopwatch:$elapsedMs" }
        override fun hideRunning() { calls += "hide" }
        override fun notifyCompleted(title: String, text: String) { calls += "notify:$title" }
        override fun ensureNotificationPermission(onDone: () -> Unit) { calls += "permission"; onDone() }
    }

    private class FakeClock(var nowMs: Long) : IstanbulClock {
        override fun now(): Instant = Instant.ofEpochMilli(nowMs)
    }

    private class Harness {
        val dir: File = Files.createTempDirectory("yks-timer-test").toFile()
        val repo = TimerStateRepository(
            PreferenceDataStoreFactory.createWithPath { File(dir, "timer_state.preferences_pb").toOkioPath() },
        )
        val sessions = mutableListOf<FocusSessionEntity>()
        val scheduler = FakeScheduler()
        val clock = FakeClock(nowMs = 1_000_000L)
        val controller = TimerController(repo, FocusSessionSink { sessions += it }, scheduler, clock)
    }

    @Test
    fun startSchedulesCompletionAndShowsCountdown() = runBlocking {
        val h = Harness()
        h.controller.start(25, PlannerCategory.entries.first())
        val endAt = 1_000_000L + 25 * 60_000L
        assertEquals(listOf("schedule:$endAt", "showCountdown:$endAt"), h.scheduler.calls)
        assertEquals(TimerPhase.RUNNING, h.repo.snapshot().phase)
    }

    @Test
    fun pauseCancelsAndHidesResumeReschedules() = runBlocking {
        val h = Harness()
        h.controller.start(10, null)
        h.scheduler.calls.clear()
        h.clock.nowMs += 60_000L
        h.controller.pause()
        assertEquals(listOf("cancel", "hide"), h.scheduler.calls)
        h.scheduler.calls.clear()
        h.clock.nowMs += 30_000L
        h.controller.resume()
        val newEnd = h.clock.nowMs + 9 * 60_000L // 10 min − 1 min already elapsed
        assertEquals(listOf("schedule:$newEnd", "showCountdown:$newEnd"), h.scheduler.calls)
    }

    @Test
    fun stopwatchNeverSchedulesAnAlarm() = runBlocking {
        val h = Harness()
        h.controller.startStopwatch(null)
        assertEquals(listOf("showStopwatch:0"), h.scheduler.calls)
        h.clock.nowMs += 90_000L
        h.scheduler.calls.clear()
        h.controller.finishStopwatch()
        assertEquals(listOf("cancel", "hide"), h.scheduler.calls)
        assertEquals(1, h.sessions.size)
        assertEquals(0, h.sessions.single().plannedMin)
        assertTrue(h.sessions.single().completed)
        assertEquals(90_000L, h.sessions.single().activeMs)
    }

    @Test
    fun finalizeCompletedOnlyAfterEndAtAndLogsCompletedSession() = runBlocking {
        val h = Harness()
        h.controller.start(2, null)
        h.scheduler.calls.clear()
        assertFalse(h.controller.finalizeCompleted()) // too early — the alarm fired early / spurious
        assertTrue(h.scheduler.calls.isEmpty())
        h.clock.nowMs += 2 * 60_000L
        assertTrue(h.controller.finalizeCompleted())
        assertEquals(listOf("cancel", "hide"), h.scheduler.calls)
        assertEquals(TimerPhase.IDLE, h.repo.snapshot().phase)
        assertTrue(h.sessions.single().completed)
        assertEquals(2, h.sessions.single().plannedMin)
        assertFalse(h.controller.finalizeCompleted()) // idempotent
    }

    @Test
    fun resetBelowOneMinuteLogsNothingButStillTearsDown() = runBlocking {
        val h = Harness()
        h.controller.start(5, null)
        h.scheduler.calls.clear()
        h.clock.nowMs += 30_000L
        h.controller.reset()
        assertEquals(listOf("cancel", "hide"), h.scheduler.calls)
        assertTrue(h.sessions.isEmpty())
        assertEquals(TimerPhase.IDLE, h.repo.snapshot().phase)
    }
}
