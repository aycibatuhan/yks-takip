package com.yks2027.tracker.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * PRD §2. All date logic goes through Europe/Istanbul explicitly (fixed UTC+3, no DST),
 * regardless of the device timezone. Injectable so rollover/countdown are unit-testable.
 */
val ISTANBUL: ZoneId = ZoneId.of("Europe/Istanbul")

interface IstanbulClock {
    fun now(): Instant
    fun today(): LocalDate = now().atZone(ISTANBUL).toLocalDate()
}

class SystemIstanbulClock : IstanbulClock {
    override fun now(): Instant = Instant.now()
}

/** Week key = epochDay of the week's Monday (PRD §6.1). Pure, unit-tested. */
fun mondayOf(date: LocalDate): Long =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()

fun dateOf(instant: Instant): LocalDate = instant.atZone(ISTANBUL).toLocalDate()
