package com.yks2027.tracker.feature.planner

import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.database.PlanWeekEntity
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.mondayOf

/**
 * PRD §6.1 — runs on every planner open. Weeks are keyed by their Monday's epochDay
 * (Istanbul); past weeks are implicitly archived because their key is in the past.
 * When a brand-new week row is created and the previous week has tasks, the caller
 * shows the one-tap "Geçen haftanın planını kopyala?" prompt.
 */
class WeekRolloverUseCase constructor(
    private val planDao: PlanDao,
    private val clock: IstanbulClock,
) {

    data class Result(
        val weekStart: Long,
        val createdNew: Boolean,
        /** Previous week key that has tasks worth copying, or null. */
        val copyableWeek: Long?,
    )

    suspend fun ensureCurrentWeek(): Result {
        val monday = mondayOf(clock.today())
        val existed = planDao.weekExists(monday)
        if (!existed) {
            planDao.insertWeek(PlanWeekEntity(monday, note = null, createdAt = clock.now().toEpochMilli()))
        }
        val copyable = if (!existed) {
            planDao.latestWeekBefore(monday)?.takeIf { planDao.taskCount(it) > 0 }
        } else {
            null
        }
        return Result(weekStart = monday, createdNew = !existed, copyableWeek = copyable)
    }

    /** Copies tasks with completion cleared (PRD §6.1 step 3). */
    suspend fun copyWeek(fromWeek: Long, toWeek: Long) {
        val now = clock.now().toEpochMilli()
        val tasks = planDao.tasksOnce(fromWeek)
        planDao.insertTasks(
            tasks.map {
                it.copy(
                    id = 0,
                    weekStartDay = toWeek,
                    isDone = false,
                    completedAt = null,
                    solvedQuestions = null,
                    createdAt = now,
                )
            },
        )
    }
}
