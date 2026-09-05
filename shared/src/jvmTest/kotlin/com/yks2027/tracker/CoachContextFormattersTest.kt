package com.yks2027.tracker

import com.yks2027.tracker.core.ai.PlanContextFormatter
import com.yks2027.tracker.core.ai.TopicContextFormatter
import com.yks2027.tracker.core.database.PlanTaskEntity
import com.yks2027.tracker.core.database.TopicStatusEntity
import com.yks2027.tracker.core.model.PlannerCategory
import com.yks2027.tracker.core.model.TopicCatalog
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.1 — what the coach sees: the week's plan day by day and the topic tracker. */
class CoachContextFormattersTest {

    @Test
    fun planListsDaysTasksAndMarksToday() {
        val monday = LocalDate.of(2026, 8, 31)
        val tasks = listOf(
            PlanTaskEntity(id = 1, weekStartDay = monday.toEpochDay(), dayOfWeek = 1, category = PlannerCategory.AYT_MAT, topic = "Türev", targetQuestions = 40, solvedQuestions = 32, isDone = true, completedAt = 1L, orderIndex = 0, createdAt = 0),
            PlanTaskEntity(id = 2, weekStartDay = monday.toEpochDay(), dayOfWeek = 3, category = PlannerCategory.AYT_FIZIK, topic = "Vektörler", targetQuestions = 20, solvedQuestions = null, isDone = false, completedAt = null, orderIndex = 0, createdAt = 0),
        )
        val text = PlanContextFormatter.format(tasks, monday.toEpochDay(), today = monday.plusDays(2))
        assertTrue(text.contains("1/2 görev tamam"))
        assertTrue(text.contains("[x] AYT Matematik — Türev 40 soru (çözülen 32)"))
        assertTrue(text.contains("Çarşamba 2 Eyl (BUGÜN):"))
        assertTrue(text.contains("[ ] AYT Fizik — Vektörler 20 soru"))
        assertTrue(!text.contains("Salı")) // empty non-today days are omitted
    }

    @Test
    fun topicTrackerReportsNeedsReviewAndConfidence() {
        val ids = TopicCatalog.all.map { it.id }
        val statuses = listOf(
            TopicStatusEntity(ids[0], studied = true, practiced = true, reviewed = false, updatedAt = 0, needsReview = true),
            TopicStatusEntity(ids[1], studied = true, practiced = false, reviewed = true, updatedAt = 0, confidence = 1),
            TopicStatusEntity(ids[2], studied = false, practiced = false, reviewed = false, updatedAt = 0, confidence = 3),
        )
        val text = TopicContextFormatter.format(statuses)
        assertTrue(text.contains("2 çalışıldı, 1 için soru çözüldü, 1 tekrar edildi"))
        assertTrue(text.contains("Tekrar gereken konular: " + TopicCatalog.labelOf(ids[0])))
        assertTrue(text.contains("zayıf hissettiği konular: " + TopicCatalog.labelOf(ids[1])))
        assertTrue(text.contains("${TopicCatalog.labelOf(ids[2])} (iyi)"))
    }
}
