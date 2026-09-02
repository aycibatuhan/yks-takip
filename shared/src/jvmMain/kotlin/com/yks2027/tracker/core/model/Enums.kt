package com.yks2027.tracker.core.model

/**
 * PRD §4.1. Persisted as TEXT by Room's built-in enum support — renaming a constant
 * is a data migration, so don't.
 */
enum class ExamKind(val label: String) {
    TYT_FULL("TYT"),
    AYT_SAY_FULL("AYT Sayısal"),
    BRANS_TYT("TYT Branş"),
    BRANS_AYT("AYT Branş");

    val isFull: Boolean get() = this == TYT_FULL || this == AYT_SAY_FULL
}

enum class Subject(
    val label: String,
    val defaultQuestionCount: Int,
    val isTyt: Boolean,
) {
    TYT_TURKCE("Türkçe", 40, true),
    TYT_SOSYAL("Sosyal Bilimler", 20, true),
    TYT_MATEMATIK("Temel Matematik", 40, true),
    TYT_FEN("Fen Bilimleri", 20, true),
    AYT_MATEMATIK("AYT Matematik", 40, false),
    AYT_FIZIK("Fizik", 14, false),
    AYT_KIMYA("Kimya", 13, false),
    AYT_BIYOLOJI("Biyoloji", 13, false),
}

/** Fixed section list for full mocks; branş kinds pick a single subject instead. */
fun ExamKind.fullSections(): List<Subject> = when (this) {
    ExamKind.TYT_FULL -> listOf(
        Subject.TYT_TURKCE, Subject.TYT_SOSYAL, Subject.TYT_MATEMATIK, Subject.TYT_FEN,
    )
    ExamKind.AYT_SAY_FULL -> listOf(
        Subject.AYT_MATEMATIK, Subject.AYT_FIZIK, Subject.AYT_KIMYA, Subject.AYT_BIYOLOJI,
    )
    ExamKind.BRANS_TYT, ExamKind.BRANS_AYT -> emptyList()
}

fun ExamKind.subjectChoices(): List<Subject> = when (this) {
    ExamKind.BRANS_TYT -> Subject.entries.filter { it.isTyt }
    ExamKind.BRANS_AYT -> Subject.entries.filter { !it.isTyt }
    else -> fullSections()
}

/**
 * PRD §6.3 — the 10 fixed planner categories. v1's TYT_BRANS/AYT_BRANS were renamed
 * to TYT_DENEME/AYT_DENEME so full mocks are schedulable too.
 */
enum class PlannerCategory(val label: String) {
    GEOMETRI("Geometri"),
    AYT_MAT("AYT Matematik"),
    TYT_MAT("TYT Matematik"),
    TYT_DENEME("TYT Deneme"),
    AYT_FIZIK("AYT Fizik"),
    AYT_KIMYA("AYT Kimya"),
    AYT_BIYOLOJI("AYT Biyoloji"),
    TYT_FEN("TYT Fen"),
    AYT_DENEME("AYT Deneme"),
    TYT_TURKCE("TYT Türkçe"),
}
