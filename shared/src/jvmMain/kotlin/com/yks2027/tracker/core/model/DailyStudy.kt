package com.yks2027.tracker.core.model

import com.yks2027.tracker.core.database.SessionSlice
import com.yks2027.tracker.core.time.dateOf
import java.time.Instant

/** v2.1 — study minutes per weekday (Mon..Sun) for a week keyed by its Monday epoch-day. Pure, unit-tested. */
object DailyStudyBuckets {
    const val DAYS = 7

    fun minutesByDay(slices: List<SessionSlice>, weekStartDay: Long): List<Int> {
        val ms = LongArray(DAYS)
        slices.forEach { slice ->
            val idx = (dateOf(Instant.ofEpochMilli(slice.startedAt)).toEpochDay() - weekStartDay).toInt()
            if (idx in 0 until DAYS) ms[idx] += slice.activeMs
        }
        return ms.map { (it / 60_000L).toInt() }
    }

    val dayLabels = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
}
