package com.yks2027.tracker.core.model

/**
 * M3 (PRD §12) — study streak. A day counts when it has any activity: a completed
 * task, a logged focus session, or a logged exam. Today not yet having activity
 * doesn't break the streak (it counts back from yesterday in that case).
 */
object StreakCalculator {

    fun compute(activityDays: Set<Long>, todayEpochDay: Long): Int {
        val anchor = when {
            todayEpochDay in activityDays -> todayEpochDay
            (todayEpochDay - 1) in activityDays -> todayEpochDay - 1
            else -> return 0
        }
        var count = 0
        while (anchor - count in activityDays) count++
        return count
    }
}
