package com.yks2027.tracker

import com.yks2027.tracker.core.model.TopicCatalog
import com.yks2027.tracker.feature.topics.TopicFilter
import com.yks2027.tracker.feature.topics.TopicFilters
import com.yks2027.tracker.feature.topics.TopicRowUi
import org.junit.Assert.assertEquals
import org.junit.Test

/** v2.1 — Konular filters: needs-review, not studied, weak (self-assessed OR exam marks). */
class TopicFiltersTest {
    private val topics = TopicCatalog.all.take(4)
    private fun row(i: Int, studied: Boolean = false, needsReview: Boolean = false, confidence: Int = 0, weak: Int = 0) =
        TopicRowUi(topics[i], studied, practiced = false, reviewed = false, needsReview = needsReview, confidence = confidence, weakWrongs = weak)

    private val rows = listOf(
        row(0, studied = true),
        row(1, needsReview = true),
        row(2, confidence = 1),
        row(3, studied = true, weak = 3),
    )

    @Test fun all() = assertEquals(4, TopicFilters.apply(rows, TopicFilter.ALL).size)
    @Test fun needsReview() = assertEquals(listOf(topics[1].id), TopicFilters.apply(rows, TopicFilter.NEEDS_REVIEW).map { it.topic.id })
    @Test fun notStudied() = assertEquals(listOf(topics[1].id, topics[2].id), TopicFilters.apply(rows, TopicFilter.NOT_STUDIED).map { it.topic.id })
    @Test fun weakUnionsSelfAssessmentAndExamMarks() =
        assertEquals(listOf(topics[2].id, topics[3].id), TopicFilters.apply(rows, TopicFilter.WEAK).map { it.topic.id })
}
