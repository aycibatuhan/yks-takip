package com.yks2027.tracker.core.ai

import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.NetCalculator
import com.yks2027.tracker.core.model.Subject
import com.yks2027.tracker.core.time.ISTANBUL
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.mondayOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * PRD §8 — the compact, user-toggleable stats summary injected into the coach's system
 * prompt. This is the whole point of building AI into this app: the coach can see the
 * student's real numbers. Nothing here is sent when ai_share_stats is off.
 */
class StatsContextBuilder constructor(
    private val examDao: ExamDao,
    private val planDao: PlanDao,
    private val focusDao: FocusDao,
    private val clock: IstanbulClock,
) {

    private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr"))

    suspend fun build(): String = buildString {
        val tyt = examDao.observeTrend(ExamKind.TYT_FULL).first()
        val ayt = examDao.observeTrend(ExamKind.AYT_SAY_FULL).first()

        appendLine("ÖĞRENCİ VERİLERİ (${clock.today().format(dateFmt)} itibarıyla):")
        appendLine(trendLine("TYT toplam net (son 5)", tyt.takeLast(5).map { it.takenAtDay to it.totalNetQuarters }))
        appendLine(trendLine("AYT SAY toplam net (son 5)", ayt.takeLast(5).map { it.takenAtDay to it.totalNetQuarters }))

        val subjectLines = Subject.entries.mapNotNull { subject ->
            val history = examDao.observeSubjectHistory(subject).first()
            val last = history.lastOrNull() ?: return@mapNotNull null
            val netQ = NetCalculator.netQuarters(last.correctCount, last.wrongCount)
            val blank = NetCalculator.blank(last.questionCount, last.correctCount, last.wrongCount)
            "${subject.label}: net ${NetCalculator.format(netQ)} (${last.correctCount}D/${last.wrongCount}Y/${blank}B)"
        }
        if (subjectLines.isNotEmpty()) {
            appendLine("Ders bazında son sonuçlar: " + subjectLines.joinToString(" · "))
        }

        val monday = mondayOf(clock.today())
        val tasks = planDao.tasksOnce(monday)
        if (tasks.isNotEmpty()) {
            val done = tasks.count { it.isDone }
            val target = tasks.sumOf { it.targetQuestions ?: 0 }
            val solved = tasks.sumOf { it.solvedQuestions ?: 0 }
            appendLine("Bu haftanın planı: $done/${tasks.size} görev tamam, hedef $target soru, çözülen $solved soru.")
        }

        val fromMs = LocalDate.ofEpochDay(monday).atStartOfDay(ISTANBUL).toInstant().toEpochMilli()
        val study = focusDao.observeActiveMsByCategory(fromMs, fromMs + 7L * 86_400_000L).first()
        if (study.isNotEmpty()) {
            val parts = study.map { "${it.category?.label ?: "Kategorisiz"} ${it.totalMs / 60_000}dk" }
            appendLine("Bu hafta odak süresi: " + parts.joinToString(" · "))
        }

        // M4: structured weak topics — the coach should target these.
        val weak = examDao.weakTopicRowsOnce()
            .groupBy { it.topicId }
            .map { (topicId, group) ->
                Triple(topicId, group.sumOf { it.wrongSum }, group.first().subject)
            }
            .sortedByDescending { it.second }
            .take(5)
        if (weak.isNotEmpty()) {
            appendLine(
                "Zayıf konular (deneme işaretlerinden): " + weak.joinToString(" · ") { (id, wrongs, subject) ->
                    "${com.yks2027.tracker.core.model.TopicCatalog.labelOf(id)} (${subject.label}, ${wrongs}Y)"
                },
            )
        }
    }.trim().take(2_200)

    private fun trendLine(label: String, points: List<Pair<Long, Int>>): String =
        if (points.isEmpty()) {
            "$label: henüz deneme yok."
        } else {
            "$label: " + points.joinToString(" → ") { (day, quarters) ->
                "${NetCalculator.format(quarters)} (${LocalDate.ofEpochDay(day).format(dateFmt)})"
            }
        }
}
