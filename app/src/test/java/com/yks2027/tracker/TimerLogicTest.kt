package com.yks2027.tracker

import com.yks2027.tracker.core.datastore.TimerLogic
import com.yks2027.tracker.core.datastore.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Test

/** PRD §13 test target 3 — timer state machine transitions. */
class TimerLogicTest {

    private val t0 = 1_000_000L

    @Test
    fun startPersistsAbsoluteEndTimestamp() {
        val s = TimerLogic.start(now = t0, plannedMin = 25, category = null, taskId = null)
        assertEquals(TimerPhase.RUNNING, s.phase)
        assertEquals(t0 + 25 * 60_000L, s.endAt)
        assertEquals(null, s.remainingMs)
        assertEquals(t0, s.startedAt)
    }

    @Test
    fun pauseStoresRemainingNotEndTimestamp() {
        val s = TimerLogic.start(t0, 25, null, null)
        val paused = TimerLogic.pause(s, now = t0 + 10 * 60_000L)
        assertEquals(TimerPhase.PAUSED, paused.phase)
        assertEquals(null, paused.endAt)
        assertEquals(15 * 60_000L, paused.remainingMs)
        assertEquals(10 * 60_000L, paused.accumulatedActiveMs)
    }

    @Test
    fun resumeRebuildsEndFromRemaining() {
        val s = TimerLogic.start(t0, 25, null, null)
        val paused = TimerLogic.pause(s, t0 + 10 * 60_000L)
        val resumedAt = t0 + 30 * 60_000L // 20-minute break
        val resumed = TimerLogic.resume(paused, resumedAt)
        assertEquals(TimerPhase.RUNNING, resumed.phase)
        assertEquals(resumedAt + 15 * 60_000L, resumed.endAt)
        assertEquals(null, resumed.remainingMs)
    }

    @Test
    fun remainingAtSurvivesProcessDeathSemantics() {
        // "Process death" = only the persisted state exists; remaining derives from wall clock.
        val s = TimerLogic.start(t0, 25, null, null)
        assertEquals(20 * 60_000L, TimerLogic.remainingAt(s, t0 + 5 * 60_000L))
        assertEquals(0L, TimerLogic.remainingAt(s, t0 + 26 * 60_000L)) // clamped, never negative
    }

    @Test
    fun activeTimeIsNetOfPauses() {
        val s = TimerLogic.start(t0, 25, null, null)
        val paused = TimerLogic.pause(s, t0 + 10 * 60_000L)      // 10 min active
        val resumed = TimerLogic.resume(paused, t0 + 40 * 60_000L) // 30 min break
        val endAt = resumed.endAt!!                                // runs the remaining 15 min
        assertEquals(25 * 60_000L, TimerLogic.activeMsIfEndedAt(resumed, endAt))
    }

    @Test
    fun abandonedWhilePausedCountsOnlyAccumulatedTime() {
        val s = TimerLogic.start(t0, 25, null, null)
        val paused = TimerLogic.pause(s, t0 + 3 * 60_000L)
        assertEquals(3 * 60_000L, TimerLogic.activeMsIfEndedAt(paused, t0 + 60 * 60_000L))
    }
}
