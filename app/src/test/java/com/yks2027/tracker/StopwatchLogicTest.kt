package com.yks2027.tracker

import com.yks2027.tracker.core.datastore.TimerLogic
import com.yks2027.tracker.core.datastore.TimerMode
import com.yks2027.tracker.core.datastore.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v1.2 — Kronometre transitions on the persisted state machine (no endAt, no alarm). */
class StopwatchLogicTest {

    private val t0 = 1_000_000L

    @Test
    fun startHasNoEndTimestamp() {
        val s = TimerLogic.startStopwatch(t0, category = null, taskId = null)
        assertEquals(TimerPhase.RUNNING, s.phase)
        assertEquals(TimerMode.STOPWATCH, s.mode)
        assertNull(s.endAt)
        assertNull(s.remainingMs)
        assertEquals(0, s.plannedMin)
        assertEquals(t0, s.startedAt)
    }

    @Test
    fun elapsedCountsUpWhileRunning() {
        val s = TimerLogic.startStopwatch(t0, null, null)
        assertEquals(90_000L, TimerLogic.elapsedAt(s, t0 + 90_000))
    }

    @Test
    fun pauseFreezesElapsedAndKeepsNoRemaining() {
        val s = TimerLogic.startStopwatch(t0, null, null)
        val paused = TimerLogic.pause(s, t0 + 120_000)
        assertEquals(TimerPhase.PAUSED, paused.phase)
        assertNull(paused.remainingMs) // countdown-only concept
        assertNull(paused.endAt)
        assertEquals(120_000L, paused.accumulatedActiveMs)
        // Wall clock advances during the pause; elapsed must not.
        assertEquals(120_000L, TimerLogic.elapsedAt(paused, t0 + 999_000))
    }

    @Test
    fun resumeContinuesFromAccumulatedNotWallClock() {
        val s = TimerLogic.startStopwatch(t0, null, null)
        val paused = TimerLogic.pause(s, t0 + 60_000)
        val resumed = TimerLogic.resume(paused, t0 + 300_000) // 4 min pause
        assertEquals(TimerPhase.RUNNING, resumed.phase)
        assertNull(resumed.endAt) // stopwatch resume must NOT synthesize an endAt
        assertEquals(60_000L + 30_000L, TimerLogic.elapsedAt(resumed, t0 + 330_000))
    }

    @Test
    fun countdownResumeStillSetsEndAt() {
        // Guard: the mode branch must not break the classic timer.
        val countdown = TimerLogic.start(t0, plannedMin = 25, category = null, taskId = null)
        assertEquals(TimerMode.COUNTDOWN, countdown.mode)
        val paused = TimerLogic.pause(countdown, t0 + 5 * 60_000)
        assertEquals(20 * 60_000L, paused.remainingMs)
        val resumed = TimerLogic.resume(paused, t0 + 10 * 60_000)
        assertEquals(t0 + 10 * 60_000 + 20 * 60_000L, resumed.endAt)
    }

    @Test
    fun elapsedSurvivesMultiplePauseResumeCycles() {
        var s = TimerLogic.startStopwatch(t0, null, null)
        s = TimerLogic.pause(s, t0 + 60_000) // +60s
        s = TimerLogic.resume(s, t0 + 100_000)
        s = TimerLogic.pause(s, t0 + 130_000) // +30s
        s = TimerLogic.resume(s, t0 + 200_000)
        assertEquals(90_000L + 10_000L, TimerLogic.elapsedAt(s, t0 + 210_000))
    }
}
