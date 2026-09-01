package com.yks2027.tracker.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.database.PlanTaskEntity
import com.yks2027.tracker.core.time.ISTANBUL
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.mondayOf
import com.yks2027.tracker.core.ui.containerColor
import com.yks2027.tracker.core.ui.contentColor
import com.yks2027.tracker.ui.PastWeekDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val shortDateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr"))

private fun weekLabel(weekStart: Long): String {
    val start = LocalDate.ofEpochDay(weekStart)
    return "${start.format(shortDateFmt)} – ${start.plusDays(6).format(shortDateFmt)}"
}

data class PastWeekRow(
    val weekStart: Long,
    val label: String,
    val total: Int,
    val done: Int,
    val target: Int,
    val solved: Int,
    val studyMinutes: Long,
    // M3 coaching report: full mocks taken that week + their mean total nets.
    val tytCount: Int = 0,
    val tytAvgQuarters: Double? = null,
    val aytCount: Int = 0,
    val aytAvgQuarters: Double? = null,
    /** v1.2 görsel paso — study minutes per day (Mon..Sun) for the row sparkline. */
    val dailyMinutes: List<Float> = emptyList(),
)

/**
 * PRD §6.5 — the coaching-review surface. Weeks are implicitly archived by their key
 * being in the past; nothing is ever moved to appear here.
 */
@HiltViewModel
class PastWeeksViewModel @Inject constructor(
    planDao: PlanDao,
    focusDao: FocusDao,
    private val examDao: com.yks2027.tracker.core.database.ExamDao,
    streakUseCase: com.yks2027.tracker.feature.dashboard.StreakUseCase,
    clock: IstanbulClock,
) : ViewModel() {

    private val currentWeek = mondayOf(clock.today())

    val rows = combine(planDao.observeWeeks(), planDao.observeWeekAggregates()) { weeks, aggregates ->
        val byWeek = aggregates.associateBy { it.weekStartDay }
        weeks.filter { it.weekStartDay < currentWeek }.map { week ->
            val agg = byWeek[week.weekStartDay]
            val from = LocalDate.ofEpochDay(week.weekStartDay)
                .atStartOfDay(ISTANBUL).toInstant().toEpochMilli()
            val tyt = examDao.trendBetween(
                com.yks2027.tracker.core.model.ExamKind.TYT_FULL, week.weekStartDay, week.weekStartDay + 6,
            )
            val ayt = examDao.trendBetween(
                com.yks2027.tracker.core.model.ExamKind.AYT_SAY_FULL, week.weekStartDay, week.weekStartDay + 6,
            )
            val slices = focusDao.sessionSlicesBetween(from, from + 7L * 86_400_000L)
            val perDayMs = LongArray(7)
            slices.forEach { slice ->
                val dayIndex = ((slice.startedAt - from) / 86_400_000L).toInt().coerceIn(0, 6)
                perDayMs[dayIndex] += slice.activeMs
            }
            PastWeekRow(
                weekStart = week.weekStartDay,
                label = weekLabel(week.weekStartDay),
                total = agg?.total ?: 0,
                done = agg?.done ?: 0,
                target = agg?.target ?: 0,
                solved = agg?.solved ?: 0,
                studyMinutes = perDayMs.sum() / 60_000,
                tytCount = tyt.size,
                tytAvgQuarters = tyt.takeIf { it.isNotEmpty() }?.map { it.totalNetQuarters }?.average(),
                aytCount = ayt.size,
                aytAvgQuarters = ayt.takeIf { it.isNotEmpty() }?.map { it.totalNetQuarters }?.average(),
                dailyMinutes = perDayMs.map { it / 60_000f },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val streak = streakUseCase.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** One-shot share payload for the system share sheet. */
    private val _shareText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val shareText = _shareText.asStateFlow()

    fun consumeShareText() {
        _shareText.value = null
    }

    /**
     * v1.2 — "Haftalık Raporu Paylaş": renders the existing weekly coaching report as
     * plain text. AGGREGATES ONLY by design — never chat messages, never notes.
     */
    fun share(row: PastWeekRow) {
        viewModelScope.launch {
            val weak = examDao.weakTopicRowsOnce()
                .groupBy { it.topicId }
                .map { (id, group) -> Triple(id, group.sumOf { it.wrongSum }, group.first().subject) }
                .sortedByDescending { it.second }
                .take(3)
            _shareText.value = buildString {
                appendLine("📚 YKS Takip — Haftalık Rapor (${row.label})")
                appendLine()
                val pct = if (row.total == 0) 0 else row.done * 100 / row.total
                appendLine("✅ Görevler: ${row.done}/${row.total} (%$pct)")
                appendLine("✏️ Sorular: hedef ${row.target}, çözülen ${row.solved}")
                appendLine("⏱️ Odaklı çalışma: ${row.studyMinutes} dk")
                if (row.tytCount > 0) {
                    appendLine(
                        "📝 TYT deneme: ${row.tytCount} (ort. net " +
                            com.yks2027.tracker.core.model.NetCalculator
                                .formatAverageQuarters(row.tytAvgQuarters ?: 0.0) + ")",
                    )
                }
                if (row.aytCount > 0) {
                    appendLine(
                        "📝 AYT deneme: ${row.aytCount} (ort. net " +
                            com.yks2027.tracker.core.model.NetCalculator
                                .formatAverageQuarters(row.aytAvgQuarters ?: 0.0) + ")",
                    )
                }
                if (streak.value > 0) appendLine("🔥 Seri: ${streak.value} gün")
                if (weak.isNotEmpty()) {
                    appendLine(
                        "🎯 Öncelikli konular: " + weak.joinToString(", ") { (id, wrongs, _) ->
                            "${com.yks2027.tracker.core.model.TopicCatalog.labelOf(id)} (${wrongs}Y)"
                        },
                    )
                }
            }.trim()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastWeeksScreen(
    onOpenWeek: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: PastWeeksViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val shareText by viewModel.shareText.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(shareText) {
        val text = shareText ?: return@LaunchedEffect
        viewModel.consumeShareText()
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Haftalık raporu paylaş"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geçmiş Haftalar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(
                    "Henüz geçmiş hafta yok. Haftalar Pazartesi günü otomatik olarak arşive geçer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // v1.2 — weeks-over-weeks trend: minutes (kolon, sol eksen) + görev (çizgi, sağ).
            if (rows.size >= 2) {
                item(key = "trend") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(
                                "Haftalık gidişat — çalışma dk (kolon, sol) · biten görev (çizgi, sağ)",
                                Modifier.padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val chronological = rows.asReversed()
                            com.yks2027.tracker.core.ui.charts.WeeklyTrendChart(
                                minutes = chronological.map { it.studyMinutes.toInt() },
                                tasksDone = chronological.map { it.done },
                                labels = chronological.map {
                                    LocalDate.ofEpochDay(it.weekStart).format(shortDateFmt)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
            items(rows, key = { it.weekStart }) { row ->
                Card(onClick = { onOpenWeek(row.weekStart) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.label,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            // v1.2 — Mon..Sun study-minute sparkline for this week.
                            if (row.dailyMinutes.any { it > 0f }) {
                                com.yks2027.tracker.core.ui.charts.Sparkline(
                                    values = row.dailyMinutes,
                                    modifier = Modifier
                                        .width(84.dp)
                                        .height(22.dp)
                                        .padding(end = 8.dp),
                                    showLastDot = false,
                                )
                            }
                            val pct = if (row.total == 0) 0 else row.done * 100 / row.total
                            Text(
                                "%$pct",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(onClick = { viewModel.share(row) }) {
                                Icon(Icons.Outlined.Share, contentDescription = "Haftalık raporu paylaş")
                            }
                        }
                        LinearProgressIndicator(
                            progress = { if (row.total == 0) 0f else row.done.toFloat() / row.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Görev: ${row.done}/${row.total}   Hedef: ${row.target} soru   " +
                                "Çözülen: ${row.solved} soru   Çalışma: ${row.studyMinutes} dk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (row.tytCount > 0 || row.aytCount > 0) {
                            val parts = buildList {
                                if (row.tytCount > 0) {
                                    add(
                                        "TYT ${row.tytCount} (ort " +
                                            com.yks2027.tracker.core.model.NetCalculator
                                                .formatAverageQuarters(row.tytAvgQuarters ?: 0.0) + ")",
                                    )
                                }
                                if (row.aytCount > 0) {
                                    add(
                                        "AYT ${row.aytCount} (ort " +
                                            com.yks2027.tracker.core.model.NetCalculator
                                                .formatAverageQuarters(row.aytAvgQuarters ?: 0.0) + ")",
                                    )
                                }
                            }
                            Text(
                                "Deneme: " + parts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class PastWeekDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    planDao: PlanDao,
) : ViewModel() {

    val weekStart: Long = savedStateHandle.toRoute<PastWeekDetailRoute>().weekStart
    val label: String = weekLabel(weekStart)

    val tasks = planDao.observeTasks(weekStart)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastWeekDetailScreen(
    onBack: () -> Unit,
    viewModel: PastWeekDetailViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val byDay = tasks.groupBy { it.dayOfWeek }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (1..7).forEach { day ->
                val dayTasks = byDay[day].orEmpty()
                if (dayTasks.isEmpty()) return@forEach
                item(key = "day-$day") {
                    Text(
                        DAY_NAMES[day - 1],
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(dayTasks, key = { it.id }) { task ->
                    ReadOnlyTaskRow(task)
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyTaskRow(task: PlanTaskEntity) {
    Surface(
        color = task.category.containerColor(),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (task.isDone) "✓" else "○",
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isDone) task.category.contentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    task.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = task.category.contentColor(),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    task.topic,
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                )
            }
            val counts = buildList {
                task.targetQuestions?.let { add("hedef $it") }
                task.solvedQuestions?.let { add("çözülen $it") }
            }
            if (counts.isNotEmpty()) {
                Text(
                    counts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
