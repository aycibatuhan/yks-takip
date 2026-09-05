package com.yks2027.tracker.core.ai

import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.NoteDao
import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.database.PlanTaskEntity
import com.yks2027.tracker.core.database.TopicDao
import com.yks2027.tracker.core.database.TopicStatusEntity
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.model.DailyStudyBuckets
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.NetCalculator
import com.yks2027.tracker.core.model.Subject
import com.yks2027.tracker.core.model.TopicCatalog
import com.yks2027.tracker.core.time.ISTANBUL
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.mondayOf
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * PRD §8 — the user-toggleable app-data summary injected into the coach's system prompt.
 * v2.1 (brother's feedback: "the coach can't see my program"): the coach now sees the
 * WHOLE app — exam dates, nets, this week's plan day by day, study minutes per day, the
 * topic tracker (needs-review / confidence / weak marks), notes and today's sessions.
 * Nothing here is sent when ai_share_stats is off.
 */
class StatsContextBuilder(
    private val examDao: ExamDao,
    private val planDao: PlanDao,
    private val focusDao: FocusDao,
    private val topicDao: TopicDao,
    private val noteDao: NoteDao,
    private val settingsRepository: SettingsRepository,
    private val clock: IstanbulClock,
) {

    private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr"))

    suspend fun build(): String = buildString {
        val today = clock.today()
        val settings = settingsRepository.settings.first()
        appendLine("ÖĞRENCİ VERİLERİ (${today.format(dateFmt)} itibarıyla, uygulamadan otomatik):")

        // 1. Exam dates
        val tytDay = Instant.ofEpochMilli(settings.tytExamAt).atZone(ISTANBUL).toLocalDate()
        val aytDay = Instant.ofEpochMilli(settings.aytExamAt).atZone(ISTANBUL).toLocalDate()
        appendLine(
            "Sınav: TYT ${tytDay.format(dateFmt)} (${ChronoUnit.DAYS.between(today, tytDay)} gün kaldı), " +
                "AYT ${aytDay.format(dateFmt)} (${ChronoUnit.DAYS.between(today, aytDay)} gün kaldı)" +
                if (settings.datesConfirmed) "." else " — tahmini, ÖSYM henüz açıklamadı.",
        )

        // 2. Nets
        val tyt = examDao.observeTrend(ExamKind.TYT_FULL).first()
        val ayt = examDao.observeTrend(ExamKind.AYT_SAY_FULL).first()
        appendLine(trendLine("TYT toplam net (son 5)", tyt.takeLast(5).map { it.takenAtDay to it.totalNetQuarters }))
        appendLine(trendLine("AYT SAY toplam net (son 5)", ayt.takeLast(5).map { it.takenAtDay to it.totalNetQuarters }))
        val subjectLines = Subject.entries.mapNotNull { subject ->
            val last = examDao.observeSubjectHistory(subject).first().lastOrNull() ?: return@mapNotNull null
            val netQ = NetCalculator.netQuarters(last.correctCount, last.wrongCount)
            val blank = NetCalculator.blank(last.questionCount, last.correctCount, last.wrongCount)
            "${subject.label} ${NetCalculator.format(netQ)} (${last.correctCount}D/${last.wrongCount}Y/${blank}B)"
        }
        if (subjectLines.isNotEmpty()) appendLine("Ders bazında son sonuçlar: " + subjectLines.joinToString(" · "))

        // 3. This week's plan, day by day
        val monday = mondayOf(today)
        val tasks = planDao.tasksOnce(monday)
        appendLine()
        appendLine(PlanContextFormatter.format(tasks, monday, today))

        // 4. Study minutes per day + per category
        val fromMs = LocalDate.ofEpochDay(monday).atStartOfDay(ISTANBUL).toInstant().toEpochMilli()
        val toMs = fromMs + 7L * 86_400_000L
        val daily = DailyStudyBuckets.minutesByDay(focusDao.sessionSlicesBetween(fromMs, toMs), monday)
        val todayIdx = today.dayOfWeek.value - 1
        appendLine(
            "Bu hafta odak süresi (dk, Pzt→Paz): " +
                daily.mapIndexed { i, m -> "${DailyStudyBuckets.dayLabels[i]} $m" + if (i == todayIdx) " (bugün)" else "" }.joinToString(" · ") +
                " — toplam ${daily.sum()} dk.",
        )
        val byCat = focusDao.observeActiveMsByCategory(fromMs, toMs).first()
        if (byCat.isNotEmpty()) {
            appendLine("Kategoriye göre: " + byCat.joinToString(" · ") { "${it.category?.label ?: "Kategorisiz"} ${it.totalMs / 60_000}dk" })
        }
        val last7From = today.minusDays(6).atStartOfDay(ISTANBUL).toInstant().toEpochMilli()
        appendLine("Son 7 gün toplam odak: ${focusDao.totalActiveMs(last7From, toMs) / 60_000} dk.")

        // 5. Topic tracker
        appendLine()
        appendLine(TopicContextFormatter.format(topicDao.statusesOnce()))
        val weak = examDao.weakTopicRowsOnce()
            .groupBy { it.topicId }
            .map { (topicId, group) -> Triple(topicId, group.sumOf { it.wrongSum }, group.first().subject) }
            .sortedByDescending { it.second }
            .take(8)
        if (weak.isNotEmpty()) {
            appendLine(
                "Denemelerde en çok yanlış işaretlenen konular: " +
                    weak.joinToString(" · ") { (id, wrongs, subject) -> "${TopicCatalog.labelOf(id)} (${subject.label}, ${wrongs}Y)" },
            )
        }

        // 6. Notes (titles only — the body may be private)
        val notes = noteDao.allOnce().sortedByDescending { it.updatedAt }.take(6)
        if (notes.isNotEmpty()) {
            appendLine()
            appendLine("Notlar (başlıklar): " + notes.joinToString(" · ") { it.title.ifBlank { "(başlıksız)" } })
        }
    }.trim().take(MAX_CHARS)

    private fun trendLine(label: String, points: List<Pair<Long, Int>>): String =
        if (points.isEmpty()) {
            "$label: henüz deneme yok."
        } else {
            "$label: " + points.joinToString(" → ") { (day, quarters) ->
                "${NetCalculator.format(quarters)} (${LocalDate.ofEpochDay(day).format(dateFmt)})"
            }
        }

    companion object {
        /** Generous but bounded: a full week of tasks + topics fits comfortably. */
        const val MAX_CHARS = 7_000
    }
}

/** Pure, unit-tested: this week's plan as a day-by-day checklist the coach can reason about. */
object PlanContextFormatter {
    private val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    private val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr"))

    fun format(tasks: List<PlanTaskEntity>, weekStartDay: Long, today: LocalDate): String = buildString {
        val done = tasks.count { it.isDone }
        appendLine(
            "BU HAFTANIN PROGRAMI (${LocalDate.ofEpochDay(weekStartDay).format(fmt)} haftası): " +
                if (tasks.isEmpty()) "henüz görev girilmemiş." else "$done/${tasks.size} görev tamam.",
        )
        for (day in 1..7) {
            val dayTasks = tasks.filter { it.dayOfWeek == day }.sortedBy { it.orderIndex }
            val date = LocalDate.ofEpochDay(weekStartDay + day - 1)
            val marker = if (date == today) " (BUGÜN)" else ""
            if (dayTasks.isEmpty()) {
                if (date == today) appendLine("- ${dayNames[day - 1]}$marker: görev yok")
                continue
            }
            appendLine("- ${dayNames[day - 1]} ${date.format(fmt)}$marker:")
            dayTasks.forEach { t ->
                val box = if (t.isDone) "[x]" else "[ ]"
                val target = t.targetQuestions?.let { " $it soru" } ?: ""
                val solved = t.solvedQuestions?.let { " (çözülen $it)" } ?: ""
                appendLine("    $box ${t.category.label} — ${t.topic}$target$solved")
            }
        }
    }.trim()
}

/** Pure, unit-tested: the topic tracker as the coach should see it. */
object TopicContextFormatter {
    private val confidenceLabel = mapOf(1 to "zayıf", 2 to "orta", 3 to "iyi")

    fun format(statuses: List<TopicStatusEntity>): String = buildString {
        val known = statuses.filter { TopicCatalog.labelOf(it.topicId) != it.topicId }
        val studied = known.count { it.studied }
        val practiced = known.count { it.practiced }
        val reviewed = known.count { it.reviewed }
        appendLine("KONU TAKİBİ: ${TopicCatalog.all.size} konudan $studied çalışıldı, $practiced için soru çözüldü, $reviewed tekrar edildi.")
        val needs = known.filter { it.needsReview }
        if (needs.isNotEmpty()) {
            appendLine("Tekrar gereken konular: " + needs.joinToString(" · ") { TopicCatalog.labelOf(it.topicId) })
        }
        val weakSelf = known.filter { it.confidence == 1 }
        if (weakSelf.isNotEmpty()) {
            appendLine("Öğrencinin zayıf hissettiği konular: " + weakSelf.joinToString(" · ") { TopicCatalog.labelOf(it.topicId) })
        }
        val rated = known.filter { it.confidence in 2..3 }
        if (rated.isNotEmpty()) {
            appendLine("Orta/iyi hissettiği: " + rated.joinToString(" · ") { "${TopicCatalog.labelOf(it.topicId)} (${confidenceLabel[it.confidence]})" })
        }
    }.trim()
}
