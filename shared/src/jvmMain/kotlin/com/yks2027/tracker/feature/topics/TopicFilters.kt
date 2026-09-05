package com.yks2027.tracker.feature.topics

/** v2.1 — Konular list filters (pure, unit-tested). */
enum class TopicFilter(val label: String) {
    ALL("Tümü"),
    NEEDS_REVIEW("Tekrar gerekenler"),
    NOT_STUDIED("Çalışılmamış"),
    WEAK("Zayıf"),
}

object TopicFilters {
    fun apply(rows: List<TopicRowUi>, filter: TopicFilter): List<TopicRowUi> = when (filter) {
        TopicFilter.ALL -> rows
        TopicFilter.NEEDS_REVIEW -> rows.filter { it.needsReview }
        TopicFilter.NOT_STUDIED -> rows.filter { !it.studied }
        // "Zayıf": self-assessed weak OR wrong marks from exams — both are reasons to revisit.
        TopicFilter.WEAK -> rows.filter { it.confidence == 1 || it.weakWrongs > 0 }
    }
}
