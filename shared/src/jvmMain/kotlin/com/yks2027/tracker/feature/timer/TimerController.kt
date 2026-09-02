package com.yks2027.tracker.feature.timer

import com.yks2027.tracker.core.database.FocusSessionEntity
import com.yks2027.tracker.core.datastore.TimerLogic
import com.yks2027.tracker.core.datastore.TimerMode
import com.yks2027.tracker.core.datastore.TimerPhase
import com.yks2027.tracker.core.datastore.TimerStateRepository
import com.yks2027.tracker.core.model.PlannerCategory
import com.yks2027.tracker.core.platform.TimerCompletionScheduler
import com.yks2027.tracker.core.time.IstanbulClock

/**
 * Single owner of timer transitions (PRD §7.2). Persists state via TimerStateRepository,
 * keeps the foreground service and the exact alarm in sync, and writes the
 * focus_sessions row when a session ends (≥60s active; sub-minute noise is discarded).
 */
/** Sink for finished sessions — FocusDao in the app, a fake in the contract test. */
fun interface FocusSessionSink {
    suspend fun insert(session: FocusSessionEntity)
}

class TimerController(
    private val timerStateRepository: TimerStateRepository,
    private val focusSessions: FocusSessionSink,
    private val scheduler: TimerCompletionScheduler,
    private val clock: IstanbulClock,
) {

    suspend fun start(plannedMin: Int, category: PlannerCategory?, isBreak: Boolean = false) {
        val now = clock.now().toEpochMilli()
        val state = TimerLogic.start(
            now, plannedMin.coerceIn(1, 300),
            category = if (isBreak) null else category,
            taskId = null,
            isBreak = isBreak,
        )
        timerStateRepository.write(state)
        scheduler.scheduleCompletion(state.endAt!!)
        scheduler.showCountdown(state.endAt)
    }

    /** v1.2 — Kronometre: count-up, no end alarm; only the FGS notification runs. */
    suspend fun startStopwatch(category: PlannerCategory?) {
        val now = clock.now().toEpochMilli()
        val state = TimerLogic.startStopwatch(now, category, taskId = null)
        timerStateRepository.write(state)
        scheduler.showStopwatch(elapsedMs = 0)
    }

    suspend fun pause() {
        val s = timerStateRepository.snapshot()
        if (s.phase != TimerPhase.RUNNING) return
        timerStateRepository.write(TimerLogic.pause(s, clock.now().toEpochMilli()))
        if (s.mode == TimerMode.COUNTDOWN) scheduler.cancelCompletion()
        scheduler.hideRunning()
    }

    suspend fun resume() {
        val s = timerStateRepository.snapshot()
        if (s.phase != TimerPhase.PAUSED) return
        val now = clock.now().toEpochMilli()
        val resumed = TimerLogic.resume(s, now)
        timerStateRepository.write(resumed)
        if (resumed.mode == TimerMode.COUNTDOWN) {
            scheduler.scheduleCompletion(resumed.endAt!!)
            scheduler.showCountdown(resumed.endAt)
        } else {
            scheduler.showStopwatch(elapsedMs = resumed.accumulatedActiveMs)
        }
    }

    /** RESET — logs an abandoned session if ≥60s of active time accumulated. */
    suspend fun reset() = endActiveSession(completed = false)

    /** v1.2 — stopwatch BİTİR: same teardown, but the session logs as completed. */
    suspend fun finishStopwatch() {
        val s = timerStateRepository.snapshot()
        if (s.mode != TimerMode.STOPWATCH) return
        endActiveSession(completed = true)
    }

    private suspend fun endActiveSession(completed: Boolean) {
        val s = timerStateRepository.snapshot()
        if (s.phase == TimerPhase.IDLE) return
        val now = clock.now().toEpochMilli()
        val activeMs = TimerLogic.activeMsIfEndedAt(s, now)
        if (!s.isBreak && activeMs >= MIN_SESSION_MS && s.startedAt != null) {
            focusSessions.insert(
                FocusSessionEntity(
                    startedAt = s.startedAt,
                    endedAt = now,
                    activeMs = activeMs,
                    // Stopwatch sessions carry plannedMin = 0 (nothing was planned).
                    plannedMin = if (s.mode == TimerMode.STOPWATCH) 0 else s.plannedMin,
                    completed = completed,
                    category = s.category,
                    taskId = null,
                    note = null,
                ),
            )
        }
        timerStateRepository.clear()
        scheduler.cancelCompletion()
        scheduler.hideRunning()
    }

    /**
     * Ran-to-zero path — called by the alarm receiver, the boot receiver (device was
     * off at end_at), and the in-app fallback tick. Idempotent via the RUNNING check.
     */
    suspend fun finalizeCompleted(): Boolean {
        val s = timerStateRepository.snapshot()
        if (s.phase != TimerPhase.RUNNING) return false
        val endAt = s.endAt ?: return false
        if (endAt > clock.now().toEpochMilli()) return false
        timerStateRepository.clear()
        val activeMs = TimerLogic.activeMsIfEndedAt(s, endAt)
        if (!s.isBreak && activeMs >= MIN_SESSION_MS && s.startedAt != null) {
            focusSessions.insert(
                FocusSessionEntity(
                    startedAt = s.startedAt,
                    endedAt = endAt,
                    activeMs = activeMs,
                    plannedMin = s.plannedMin,
                    completed = true,
                    category = s.category,
                    taskId = null,
                    note = null,
                ),
            )
        }
        scheduler.cancelCompletion()
        scheduler.hideRunning()
        return true
    }

    companion object {
        const val MIN_SESSION_MS = 60_000L
    }
}
