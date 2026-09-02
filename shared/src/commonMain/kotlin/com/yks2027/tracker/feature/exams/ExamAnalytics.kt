package com.yks2027.tracker.feature.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.SubjectPoint
import com.yks2027.tracker.core.database.TrendPoint
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.NetCalculator
import com.yks2027.tracker.core.model.Subject
import com.yks2027.tracker.core.ui.charts.ChartLegend
import com.yks2027.tracker.core.ui.charts.ChartPoint
import com.yks2027.tracker.core.ui.charts.CountsColumnChart
import com.yks2027.tracker.core.ui.charts.NetTrendChart
import com.yks2027.tracker.core.ui.charts.PercentTrendChart
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr"))

data class AnalyticsUiState(
    val selectedKind: ExamKind = ExamKind.TYT_FULL,
    val points: List<ChartPoint> = emptyList(),
    val lastTyt: Int? = null,
    val bestTyt: Int? = null,
    val lastAyt: Int? = null,
    val bestAyt: Int? = null,
    val totalCount: Int = 0,
    val deltaQuarters: Int? = null,
    /** Mean of the last five total nets, in quarter-units (fractional). */
    val avgLast5Quarters: Double? = null,
)

data class SubjectUiState(
    val subject: Subject = Subject.TYT_MATEMATIK,
    val netPoints: List<ChartPoint> = emptyList(),
    val accuracyPercents: List<Int> = emptyList(),
    val accuracyLabels: List<String> = emptyList(),
    val yanlis: List<Int> = emptyList(),
    val bos: List<Int> = emptyList(),
    val countLabels: List<String> = emptyList(),
    val lastNetQuarters: Int? = null,
    val bestNetQuarters: Int? = null,
    val avgAccuracy: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    examDao: ExamDao,
) : ViewModel() {

    private val selectedKind = MutableStateFlow(ExamKind.TYT_FULL)
    private val selectedSubject = MutableStateFlow(Subject.TYT_MATEMATIK)
    val subjectSelection = selectedSubject.asStateFlow()

    val ui = combine(
        examDao.observeTrend(ExamKind.TYT_FULL),
        examDao.observeTrend(ExamKind.AYT_SAY_FULL),
        examDao.observeCount(),
        selectedKind,
    ) { tyt, ayt, count, kind ->
        val trend = if (kind == ExamKind.TYT_FULL) tyt else ayt
        AnalyticsUiState(
            selectedKind = kind,
            points = trend.map { it.toChartPoint() },
            lastTyt = tyt.lastOrNull()?.totalNetQuarters,
            bestTyt = tyt.maxOfOrNull { it.totalNetQuarters },
            lastAyt = ayt.lastOrNull()?.totalNetQuarters,
            bestAyt = ayt.maxOfOrNull { it.totalNetQuarters },
            totalCount = count,
            deltaQuarters = if (trend.size >= 2) {
                trend.last().totalNetQuarters - trend[trend.size - 2].totalNetQuarters
            } else {
                null
            },
            avgLast5Quarters = trend.takeLast(5)
                .takeIf { it.isNotEmpty() }
                ?.map { it.totalNetQuarters }
                ?.average(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    val subjectUi = selectedSubject
        .flatMapLatest { subject ->
            examDao.observeSubjectHistory(subject).map { history -> buildSubjectUi(subject, history) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectUiState())

    /** M3 — legacy free-text eksik-konu notes for the selected subject. */
    val subjectNotes = selectedSubject
        .flatMapLatest { examDao.observeNotesBySubject(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<com.yks2027.tracker.core.database.SubjectNote>())

    /** M4 — structured weak-topic ranking from exam_topic_marks. */
    val weakTopicsAll = examDao.observeWeakTopicRows()
        .map { rows -> foldWeakTopics(rows).take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weakTopicsForSubject = combine(examDao.observeWeakTopicRows(), selectedSubject) { rows, subject ->
        foldWeakTopics(rows.filter { it.subject == subject })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(kind: ExamKind) {
        selectedKind.value = kind
    }

    fun selectSubject(subject: Subject) {
        selectedSubject.value = subject
    }

    private fun buildSubjectUi(subject: Subject, history: List<SubjectPoint>): SubjectUiState {
        val labels = history.map { LocalDate.ofEpochDay(it.takenAtDay).format(shortDate) }
        val nets = history.map { NetCalculator.netQuarters(it.correctCount, it.wrongCount) }
        val accuracyPairs = history.mapIndexedNotNull { i, p ->
            NetCalculator.accuracyPercent(p.correctCount, p.wrongCount)?.let { labels[i] to it }
        }
        return SubjectUiState(
            subject = subject,
            netPoints = history.mapIndexed { i, _ -> ChartPoint(labels[i], nets[i]) },
            accuracyPercents = accuracyPairs.map { it.second },
            accuracyLabels = accuracyPairs.map { it.first },
            yanlis = history.map { it.wrongCount },
            bos = history.map { NetCalculator.blank(it.questionCount, it.correctCount, it.wrongCount) },
            countLabels = labels,
            lastNetQuarters = nets.lastOrNull(),
            bestNetQuarters = nets.maxOrNull(),
            avgAccuracy = accuracyPairs.map { it.second }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        )
    }

    private fun TrendPoint.toChartPoint() = ChartPoint(
        label = LocalDate.ofEpochDay(takenAtDay).format(shortDate),
        quarters = totalNetQuarters,
    )

    companion object {
        /** Folds per-(topic, errorType) rows into per-topic summaries, worst first. */
        fun foldWeakTopics(rows: List<com.yks2027.tracker.core.database.WeakTopicRow>): List<WeakTopicUi> =
            rows.groupBy { it.topicId }.map { (topicId, group) ->
                WeakTopicUi(
                    topicId = topicId,
                    subject = group.first().subject,
                    label = com.yks2027.tracker.core.model.TopicCatalog.labelOf(topicId),
                    wrong = group.sumOf { it.wrongSum },
                    blank = group.sumOf { it.blankSum },
                    // Exam counts overlap across error-type slices; max is a safe floor.
                    examCount = group.maxOf { it.examCount },
                    lastDay = group.maxOf { it.lastDay },
                    errorBreakdown = group
                        .filter { it.errorType != null && it.wrongSum > 0 }
                        .associate { it.errorType!! to it.wrongSum },
                )
            }.sortedWith(compareByDescending<WeakTopicUi> { it.wrong }.thenByDescending { it.blank })
    }
}

data class WeakTopicUi(
    val topicId: String,
    val subject: Subject,
    val label: String,
    val wrong: Int,
    val blank: Int,
    val examCount: Int,
    val lastDay: Long,
    val errorBreakdown: Map<String, Int>,
)

/** Analiz (PRD §4.4): total-net trend + KPIs, and the M2 per-subject deep dive. */
@Composable
fun AnalyticsTab(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val subjectUi by viewModel.subjectUi.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ui.selectedKind == ExamKind.TYT_FULL,
                onClick = { viewModel.select(ExamKind.TYT_FULL) },
                label = { Text("TYT Toplam Net") },
            )
            FilterChip(
                selected = ui.selectedKind == ExamKind.AYT_SAY_FULL,
                onClick = { viewModel.select(ExamKind.AYT_SAY_FULL) },
                label = { Text("AYT Toplam Net") },
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { KpiCard("Son TYT", ui.lastTyt) }
            item { KpiCard("En Yüksek TYT", ui.bestTyt) }
            item { KpiCard("Son AYT", ui.lastAyt) }
            item { KpiCard("En Yüksek AYT", ui.bestAyt) }
            item { KpiCard("Toplam Deneme", null, raw = ui.totalCount.toString()) }
            item { KpiCard("Δ Önceki", ui.deltaQuarters, signed = true) }
            item {
                KpiCard(
                    "Son 5 Ort.",
                    null,
                    raw = ui.avgLast5Quarters?.let(NetCalculator::formatAverageQuarters) ?: "—",
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            // Branş denemes are excluded from the total-net series (PRD §4.4 series rule).
            NetTrendChart(
                points = ui.points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(12.dp),
            )
        }

        val weakAll by viewModel.weakTopicsAll.collectAsStateWithLifecycle()
        if (weakAll.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Zayıf Konular (tüm dersler)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    WeakTopicList(weakAll, showSubject = true)
                }
            }
        }

        SubjectAnalyticsSection(
            state = subjectUi,
            onSelectSubject = viewModel::selectSubject,
        )

        val weakSubject by viewModel.weakTopicsForSubject.collectAsStateWithLifecycle()
        if (weakSubject.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Zayıf Konular — ${subjectUi.subject.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    WeakTopicList(weakSubject, showSubject = false)
                }
            }
        }

        val notes by viewModel.subjectNotes.collectAsStateWithLifecycle()
        if (notes.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Serbest notlar — ${subjectUi.subject.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    notes.forEach { note ->
                        Text(
                            "• ${note.note}  (${LocalDate.ofEpochDay(note.takenAtDay).format(shortDate)})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * v1.2 görsel paso — each topic renders a horizontal bar (length = wrongs relative to
 * the worst topic) split by hata türü; wrongs without a diagnosis show neutral.
 * Numbers stay alongside — the bar is a comparison aid, not the data.
 */
@Composable
private fun WeakTopicList(topics: List<WeakTopicUi>, showSubject: Boolean) {
    val maxWrong = topics.maxOfOrNull { it.wrong }?.coerceAtLeast(1) ?: 1
    topics.forEach { topic ->
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (showSubject) "${topic.label} · ${topic.subject.label}" else topic.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${topic.examCount} denemede · son: ${LocalDate.ofEpochDay(topic.lastDay).format(shortDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    buildString {
                        append("${topic.wrong}Y")
                        if (topic.blank > 0) append(" ${topic.blank}B")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val diagnosed = topic.errorBreakdown.values.sum()
            val undiagnosed = (topic.wrong - diagnosed).coerceAtLeast(0)
            val segments = buildList {
                topic.errorBreakdown.forEach { (type, count) ->
                    add(
                        com.yks2027.tracker.core.ui.charts.BarSegment(
                            count.toFloat(),
                            com.yks2027.tracker.core.ui.charts.ErrorTypeColors.forName(type),
                        ),
                    )
                }
                if (undiagnosed > 0) {
                    add(
                        com.yks2027.tracker.core.ui.charts.BarSegment(
                            undiagnosed.toFloat(),
                            com.yks2027.tracker.core.ui.charts.ErrorTypeColors.belirsiz,
                        ),
                    )
                }
            }
            com.yks2027.tracker.core.ui.charts.SegmentedBar(
                segments = segments,
                fillFraction = topic.wrong.toFloat() / maxWrong,
                modifier = Modifier.fillMaxWidth(),
            )
            if (topic.errorBreakdown.isNotEmpty()) {
                com.yks2027.tracker.core.ui.charts.SegmentLegend(
                    entries = topic.errorBreakdown.map { (type, count) ->
                        Triple(
                            runCatching {
                                com.yks2027.tracker.core.model.ErrorType.valueOf(type).label
                            }.getOrDefault(type),
                            com.yks2027.tracker.core.ui.charts.ErrorTypeColors.forName(type),
                            count.toString(),
                        )
                    } + if (undiagnosed > 0) {
                        listOf(
                            Triple(
                                "Belirsiz",
                                com.yks2027.tracker.core.ui.charts.ErrorTypeColors.belirsiz,
                                undiagnosed.toString(),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                )
            }
        }
    }
}

@Composable
private fun SubjectAnalyticsSection(
    state: SubjectUiState,
    onSelectSubject: (Subject) -> Unit,
) {
    Text("Ders Analizi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    var menuOpen by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menuOpen = true }) { Text(state.subject.label) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Subject.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = {
                    onSelectSubject(s)
                    menuOpen = false
                })
            }
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { KpiCard("Son Net", state.lastNetQuarters) }
        item { KpiCard("En İyi Net", state.bestNetQuarters) }
        item {
            KpiCard("Ort. Doğruluk", null, raw = state.avgAccuracy?.let { "%$it" } ?: "—")
        }
    }

    ChartCard("Net gelişimi (deneme + branş)") {
        NetTrendChart(
            points = state.netPoints,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(12.dp),
        )
    }

    ChartCard("Doğruluk %") {
        PercentTrendChart(
            percents = state.accuracyPercents,
            labels = state.accuracyLabels,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(12.dp),
        )
    }

    ChartCard("Boş vs Yanlış") {
        Column {
            CountsColumnChart(
                yanlis = state.yanlis,
                bos = state.bos,
                labels = state.countLabels,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(12.dp),
            )
            ChartLegend(
                entries = listOf(
                    "Yanlış" to com.yks2027.tracker.core.ui.charts.CountsChartColors.yanlis,
                    "Boş" to com.yks2027.tracker.core.ui.charts.CountsChartColors.bos,
                ),
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 8.dp)) {
            Text(
                title,
                Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun KpiCard(label: String, quarters: Int?, raw: String? = null, signed: Boolean = false) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val text = raw ?: quarters?.let {
                val formatted = NetCalculator.format(it)
                if (signed && it > 0) "+$formatted" else formatted
            } ?: "—"
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if ((quarters ?: 0) < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
