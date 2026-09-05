package com.yks2027.tracker

import com.yks2027.tracker.core.database.SessionSlice
import com.yks2027.tracker.core.model.DailyStudyBuckets
import com.yks2027.tracker.core.time.ISTANBUL
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** v2.1 — study minutes per weekday, Istanbul day boundaries, out-of-week slices ignored. */
class DailyStudyBucketsTest {
    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(ISTANBUL).toInstant().toEpochMilli()

    @Test
    fun bucketsByIstanbulDayAndSumsMinutes() {
        val monday = LocalDate.of(2026, 8, 31) // a Monday
        val slices = listOf(
            SessionSlice(at(monday, 9), 25 * 60_000L),
            SessionSlice(at(monday, 21, 30), 35 * 60_000L),
            SessionSlice(at(monday.plusDays(2), 0, 5), 90 * 60_000L), // Wed 00:05 Istanbul
            SessionSlice(at(monday.plusDays(6), 23, 50), 10 * 60_000L), // Sun late
            SessionSlice(at(monday.minusDays(1), 12), 60 * 60_000L), // previous week — ignored
            SessionSlice(at(monday.plusDays(7), 12), 60 * 60_000L), // next week — ignored
        )
        assertEquals(listOf(60, 0, 90, 0, 0, 0, 10), DailyStudyBuckets.minutesByDay(slices, monday.toEpochDay()))
    }

    @Test
    fun emptyWeekIsSevenZeros() {
        assertEquals(List(7) { 0 }, DailyStudyBuckets.minutesByDay(emptyList(), 0))
    }
}
