package com.yks2027.tracker.feature.dashboard

import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.model.StreakCalculator
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.dateOf
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class StreakUseCase constructor(
    private val examDao: ExamDao,
    private val planDao: PlanDao,
    private val focusDao: FocusDao,
    private val clock: IstanbulClock,
) {

    fun observe(): Flow<Int> = combine(
        examDao.observeCreatedTimes(),
        planDao.observeCompletionTimes(),
        focusDao.observeStartTimes(),
    ) { examTimes, taskTimes, focusTimes ->
        val days = (examTimes.asSequence() + taskTimes.asSequence() + focusTimes.asSequence())
            .map { dateOf(Instant.ofEpochMilli(it)).toEpochDay() }
            .toSet()
        StreakCalculator.compute(days, clock.today().toEpochDay())
    }

    /**
     * v1.2 heat strip — per-day activity counts from the SAME sources as the streak
     * (exams logged + tasks completed + focus sessions started), so strip and KPI agree.
     */
    fun observeDayCounts(): Flow<Map<Long, Int>> = combine(
        examDao.observeCreatedTimes(),
        planDao.observeCompletionTimes(),
        focusDao.observeStartTimes(),
    ) { examTimes, taskTimes, focusTimes ->
        (examTimes.asSequence() + taskTimes.asSequence() + focusTimes.asSequence())
            .map { dateOf(Instant.ofEpochMilli(it)).toEpochDay() }
            .groupingBy { it }
            .eachCount()
    }
}
