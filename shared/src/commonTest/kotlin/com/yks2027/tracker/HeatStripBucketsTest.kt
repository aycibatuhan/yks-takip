package com.yks2027.tracker

import com.yks2027.tracker.core.model.HeatStripBuckets
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v1.2 — heat-strip grid bucketing (Monday-start weeks, future days null). */
class HeatStripBucketsTest {

    // 2026-08-30 is a Sunday; its week starts Monday 2026-08-24.
    private val sunday = LocalDate.parse("2026-08-30")
    private val monday = LocalDate.parse("2026-08-24")

    @Test
    fun gridShapeIsWeeksColumnsBySevenRows() {
        val grid = HeatStripBuckets.grid(emptyMap(), sunday, weeks = 8)
        assertEquals(8, grid.size)
        grid.forEach { assertEquals(7, it.size) }
    }

    @Test
    fun countsLandOnTheRightCell() {
        val wed = LocalDate.parse("2026-08-26") // week row index 2 (Mon=0)
        val grid = HeatStripBuckets.grid(mapOf(wed.toEpochDay() to 3), sunday, weeks = 2)
        assertEquals(3, grid[1][2]) // last column = current week
        assertEquals(0, grid[1][0]) // Monday of current week: no activity but past → 0
    }

    @Test
    fun futureDaysAreNullNotZero() {
        // Query the grid as of WEDNESDAY: Thu..Sun of the current week are future.
        val wednesday = LocalDate.parse("2026-08-26")
        val grid = HeatStripBuckets.grid(emptyMap(), wednesday, weeks = 2)
        assertEquals(0, grid[1][2]) // Wednesday itself: past/today → 0
        assertNull(grid[1][3]) // Thursday → future
        assertNull(grid[1][6]) // Sunday → future
        assertEquals(0, grid[0][6]) // previous week fully in the past
    }

    @Test
    fun sundayTodayMakesWholeCurrentWeekVisible() {
        val grid = HeatStripBuckets.grid(emptyMap(), sunday, weeks = 1)
        grid[0].forEach { cell -> assertEquals(0, cell) } // no nulls on a completed week
    }

    @Test
    fun oldestColumnStartsExactlyWeeksMinusOneWeeksBack() {
        val grid = HeatStripBuckets.grid(
            mapOf(monday.minusWeeks(7).toEpochDay() to 5),
            sunday,
            weeks = 8,
        )
        assertEquals(5, grid[0][0]) // 7 weeks ago Monday = first cell
    }

    @Test
    fun levelsBucketSanely() {
        assertEquals(0, HeatStripBuckets.level(0))
        assertEquals(1, HeatStripBuckets.level(1))
        assertEquals(2, HeatStripBuckets.level(3))
        assertEquals(3, HeatStripBuckets.level(5))
        assertEquals(4, HeatStripBuckets.level(9))
    }
}
