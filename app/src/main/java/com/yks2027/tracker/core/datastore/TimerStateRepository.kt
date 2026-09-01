package com.yks2027.tracker.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yks2027.tracker.core.model.PlannerCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class TimerPhase { IDLE, RUNNING, PAUSED }

/** v1.2 — COUNTDOWN is the classic timer; STOPWATCH counts up with no end alarm. */
enum class TimerMode { COUNTDOWN, STOPWATCH }

/**
 * PRD §7.2 — the persisted timer state machine. RUNNING stores the absolute wall-clock
 * end timestamp (survives reboot); PAUSED stores remaining milliseconds (an
 * end-timestamp cannot represent a paused timer). Written only on transitions.
 * STOPWATCH mode (v1.2) has no endAt/remainingMs — elapsed time is reconstructed from
 * accumulatedActiveMs + lastResumedAt, which also survives process death and reboot.
 */
data class TimerState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val mode: TimerMode = TimerMode.COUNTDOWN,
    val endAt: Long? = null,
    val remainingMs: Long? = null,
    val plannedMin: Int = 25,
    val category: PlannerCategory? = null,
    val linkedTaskId: Long? = null,
    val startedAt: Long? = null,
    val accumulatedActiveMs: Long = 0,
    val lastResumedAt: Long? = null,
    /** M3 auto-break: break runs are never logged as focus_sessions. */
    val isBreak: Boolean = false,
)

/** Pure transition logic — unit-tested (PRD §13 test target 3). */
object TimerLogic {

    fun start(
        now: Long,
        plannedMin: Int,
        category: PlannerCategory?,
        taskId: Long?,
        isBreak: Boolean = false,
    ): TimerState =
        TimerState(
            phase = TimerPhase.RUNNING,
            endAt = now + plannedMin * 60_000L,
            remainingMs = null,
            plannedMin = plannedMin,
            category = category,
            linkedTaskId = taskId,
            startedAt = now,
            accumulatedActiveMs = 0,
            lastResumedAt = now,
            isBreak = isBreak,
        )

    /** v1.2 — stopwatch start: no end timestamp, no alarm; elapsed builds from zero. */
    fun startStopwatch(now: Long, category: PlannerCategory?, taskId: Long?): TimerState =
        TimerState(
            phase = TimerPhase.RUNNING,
            mode = TimerMode.STOPWATCH,
            endAt = null,
            remainingMs = null,
            plannedMin = 0,
            category = category,
            linkedTaskId = taskId,
            startedAt = now,
            accumulatedActiveMs = 0,
            lastResumedAt = now,
            isBreak = false,
        )

    fun pause(s: TimerState, now: Long): TimerState {
        check(s.phase == TimerPhase.RUNNING) { "pause() requires RUNNING" }
        return s.copy(
            phase = TimerPhase.PAUSED,
            remainingMs = if (s.mode == TimerMode.COUNTDOWN) ((s.endAt ?: now) - now).coerceAtLeast(0) else null,
            endAt = null,
            accumulatedActiveMs = s.accumulatedActiveMs + (now - (s.lastResumedAt ?: now)),
            lastResumedAt = null,
        )
    }

    fun resume(s: TimerState, now: Long): TimerState {
        check(s.phase == TimerPhase.PAUSED) { "resume() requires PAUSED" }
        return s.copy(
            phase = TimerPhase.RUNNING,
            endAt = if (s.mode == TimerMode.COUNTDOWN) now + (s.remainingMs ?: 0) else null,
            remainingMs = null,
            lastResumedAt = now,
        )
    }

    fun remainingAt(s: TimerState, now: Long): Long = when (s.phase) {
        TimerPhase.RUNNING -> ((s.endAt ?: now) - now).coerceAtLeast(0)
        TimerPhase.PAUSED -> s.remainingMs ?: 0
        TimerPhase.IDLE -> 0
    }

    /** Stopwatch display value — identical to active study time so far. */
    fun elapsedAt(s: TimerState, now: Long): Long = activeMsIfEndedAt(s, now)

    /** Active study time if the session ends at [endedAt] (net of pauses). */
    fun activeMsIfEndedAt(s: TimerState, endedAt: Long): Long = when (s.phase) {
        TimerPhase.RUNNING -> s.accumulatedActiveMs + (endedAt - (s.lastResumedAt ?: endedAt)).coerceAtLeast(0)
        TimerPhase.PAUSED -> s.accumulatedActiveMs
        TimerPhase.IDLE -> 0
    }
}

/** PRD §9.3 — separate preferences file so timer writes don't wake settings collectors. */
private val Context.timerStore: DataStore<Preferences> by preferencesDataStore(name = "timer_state")

@Singleton
class TimerStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val PHASE = stringPreferencesKey("state")
        val MODE = stringPreferencesKey("mode")
        val END_AT = longPreferencesKey("end_at_epoch_ms")
        val REMAINING_MS = longPreferencesKey("remaining_ms")
        val PLANNED_MIN = intPreferencesKey("planned_min")
        val CATEGORY = stringPreferencesKey("category")
        val LINKED_TASK_ID = longPreferencesKey("linked_task_id")
        val STARTED_AT = longPreferencesKey("started_at")
        val ACCUM_ACTIVE_MS = longPreferencesKey("accumulated_active_ms")
        val LAST_RESUMED_AT = longPreferencesKey("last_resumed_at")
        val IS_BREAK = androidx.datastore.preferences.core.booleanPreferencesKey("is_break")
    }

    val state: Flow<TimerState> = context.timerStore.data.map { p ->
        TimerState(
            phase = p[Keys.PHASE]?.let { runCatching { TimerPhase.valueOf(it) }.getOrNull() }
                ?: TimerPhase.IDLE,
            mode = p[Keys.MODE]?.let { runCatching { TimerMode.valueOf(it) }.getOrNull() }
                ?: TimerMode.COUNTDOWN,
            endAt = p[Keys.END_AT],
            remainingMs = p[Keys.REMAINING_MS],
            plannedMin = p[Keys.PLANNED_MIN] ?: 25,
            category = p[Keys.CATEGORY]?.let { runCatching { PlannerCategory.valueOf(it) }.getOrNull() },
            linkedTaskId = p[Keys.LINKED_TASK_ID],
            startedAt = p[Keys.STARTED_AT],
            accumulatedActiveMs = p[Keys.ACCUM_ACTIVE_MS] ?: 0,
            lastResumedAt = p[Keys.LAST_RESUMED_AT],
            isBreak = p[Keys.IS_BREAK] ?: false,
        )
    }

    suspend fun snapshot(): TimerState = state.first()

    suspend fun write(s: TimerState) {
        context.timerStore.edit { p ->
            p[Keys.PHASE] = s.phase.name
            p[Keys.MODE] = s.mode.name
            s.endAt?.let { p[Keys.END_AT] = it } ?: p.remove(Keys.END_AT)
            s.remainingMs?.let { p[Keys.REMAINING_MS] = it } ?: p.remove(Keys.REMAINING_MS)
            p[Keys.PLANNED_MIN] = s.plannedMin
            s.category?.let { p[Keys.CATEGORY] = it.name } ?: p.remove(Keys.CATEGORY)
            s.linkedTaskId?.let { p[Keys.LINKED_TASK_ID] = it } ?: p.remove(Keys.LINKED_TASK_ID)
            s.startedAt?.let { p[Keys.STARTED_AT] = it } ?: p.remove(Keys.STARTED_AT)
            p[Keys.ACCUM_ACTIVE_MS] = s.accumulatedActiveMs
            s.lastResumedAt?.let { p[Keys.LAST_RESUMED_AT] = it } ?: p.remove(Keys.LAST_RESUMED_AT)
            p[Keys.IS_BREAK] = s.isBreak
        }
    }

    suspend fun clear() {
        context.timerStore.edit { it.clear() }
    }
}
