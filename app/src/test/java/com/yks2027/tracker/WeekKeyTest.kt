package com.yks2027.tracker

import com.yks2027.tracker.core.time.dateOf
import com.yks2027.tracker.core.time.mondayOf
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** PRD §13 test target 2 — the Sunday-midnight Istanbul week boundary. */
class WeekKeyTest {

    @Test
    fun sundayBelongsToItsOwnWeek() {
        // 2026-08-30 is a Sunday; its week's Monday is 2026-08-24.
        assertEquals(
            LocalDate.of(2026, 8, 24).toEpochDay(),
            mondayOf(LocalDate.of(2026, 8, 30)),
        )
    }

    @Test
    fun mondayIsItsOwnKey() {
        assertEquals(
            LocalDate.of(2026, 8, 31).toEpochDay(),
            mondayOf(LocalDate.of(2026, 8, 31)),
        )
    }

    @Test
    fun istanbulMidnightBoundary() {
        // Sunday 23:59 vs Monday 00:01 in Europe/Istanbul (UTC+3) must land in
        // different weeks — 20:59Z and 21:01Z on the same UTC day straddle it.
        val sundayLate = dateOf(Instant.parse("2026-08-30T20:59:00Z")) // 23:59+03 Sunday
        val mondayEarly = dateOf(Instant.parse("2026-08-30T21:01:00Z")) // 00:01+03 Monday

        assertNotEquals(mondayOf(sundayLate), mondayOf(mondayEarly))
        assertEquals(LocalDate.of(2026, 8, 24).toEpochDay(), mondayOf(sundayLate))
        assertEquals(LocalDate.of(2026, 8, 31).toEpochDay(), mondayOf(mondayEarly))
    }
}
