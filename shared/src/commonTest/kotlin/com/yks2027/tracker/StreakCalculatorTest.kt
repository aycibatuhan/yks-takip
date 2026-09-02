package com.yks2027.tracker

import com.yks2027.tracker.core.model.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/** M3 — streak semantics: consecutive active days; an idle today doesn't break it. */
class StreakCalculatorTest {

    private val today = 20_700L

    @Test
    fun emptyActivityMeansZero() {
        assertEquals(0, StreakCalculator.compute(emptySet(), today))
    }

    @Test
    fun countsBackFromToday() {
        val days = setOf(today, today - 1, today - 2)
        assertEquals(3, StreakCalculator.compute(days, today))
    }

    @Test
    fun idleTodayCountsFromYesterday() {
        val days = setOf(today - 1, today - 2, today - 3)
        assertEquals(3, StreakCalculator.compute(days, today))
    }

    @Test
    fun gapBreaksStreak() {
        val days = setOf(today, today - 1, today - 3, today - 4)
        assertEquals(2, StreakCalculator.compute(days, today))
    }

    @Test
    fun twoIdleDaysMeansZero() {
        val days = setOf(today - 2, today - 3)
        assertEquals(0, StreakCalculator.compute(days, today))
    }
}
