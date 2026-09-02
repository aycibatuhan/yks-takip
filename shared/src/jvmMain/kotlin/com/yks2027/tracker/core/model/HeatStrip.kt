package com.yks2027.tracker.core.model

import com.yks2027.tracker.core.time.mondayOf
import java.time.LocalDate

/**
 * v1.2 görsel paso — GitHub-style activity heat strip bucketing. Pure and unit-tested;
 * the composable only paints the grid this returns.
 */
object HeatStripBuckets {

    /**
     * Grid of the last [weeks] ISO weeks (Istanbul), oldest column first. Each column
     * holds 7 cells Monday→Sunday: the activity count for that day, or null for days
     * after [today] (rendered as empty, not zero — the week isn't over yet).
     */
    fun grid(countsByEpochDay: Map<Long, Int>, today: LocalDate, weeks: Int): List<List<Int?>> {
        require(weeks >= 1)
        val currentMonday = mondayOf(today)
        val todayDay = today.toEpochDay()
        return (weeks - 1 downTo 0).map { weeksAgo ->
            val weekStart = currentMonday - weeksAgo * 7L
            (0..6).map { dayIndex ->
                val day = weekStart + dayIndex
                if (day > todayDay) null else countsByEpochDay[day] ?: 0
            }
        }
    }

    /** Intensity bucket 0..4 for a day's activity count (exams + tasks + sessions). */
    fun level(count: Int): Int = when {
        count <= 0 -> 0
        count == 1 -> 1
        count <= 3 -> 2
        count <= 5 -> 3
        else -> 4
    }
}
