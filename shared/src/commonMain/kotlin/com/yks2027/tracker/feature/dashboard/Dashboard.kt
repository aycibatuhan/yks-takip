package com.yks2027.tracker.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.database.PlanTaskEntity
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.model.NetCalculator
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.time.mondayOf
import com.yks2027.tracker.core.ui.containerColor
import com.yks2027.tracker.core.ui.contentColor
import com.yks2027.tracker.core.ui.isExpandedWidth
import com.yks2027.tracker.feature.planner.SolvedCaptureDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Countdown(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val past: Boolean,
)

data class DashboardUiState(
    val tyt: Countdown = Countdown(0, 0, 0, 0, false),
    val ayt: Countdown = Countdown(0, 0, 0, 0, false),
    val datesConfirmed: Boolean = true,
    val yearLabel: String = "YKS",
    val lastTytQuarters: Int? = null,
    val lastAytQuarters: Int? = null,
    /** v1.2 görsel paso — last ~10 total nets, render-ready floats. */
    val tytSpark: List<Float> = emptyList(),
    val aytSpark: List<Float> = emptyList(),
    val todayTasks: List<PlanTaskEntity> = emptyList(),
    val weekDone: Int = 0,
    val weekTotal: Int = 0,
    /** Days since the last JSON backup; null = never backed up. */
    val daysSinceBackup: Long? = null,
    val autoBackupConfigured: Boolean = false,
    /** Consecutive active days (task done / focus session / exam logged). */
    val streak: Int = 0,
    /** v1.2 — last 8 ISO weeks of activity (columns old→new, rows Mon..Sun). */
    val heatGrid: List<List<Int?>> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    examDao: ExamDao,
    private val planDao: PlanDao,
    private val clock: IstanbulClock,
    streakUseCase: StreakUseCase,
) : ViewModel() {

    private val tick = flow {
        while (true) {
            emit(clock.now())
            delay(1_000)
        }
    }

    private val weekStart = mondayOf(clock.today())
    private val todayIso = clock.today().dayOfWeek.value

    val ui = combine(
        settingsRepository.settings,
        tick,
        examDao.observeTrend(com.yks2027.tracker.core.model.ExamKind.TYT_FULL),
        examDao.observeTrend(com.yks2027.tracker.core.model.ExamKind.AYT_SAY_FULL),
        planDao.observeTasks(weekStart),
    ) { settings, now, tytTrend, aytTrend, weekTasks ->
        val nowMs = now.toEpochMilli()
        DashboardUiState(
            tyt = countdownTo(settings.tytExamAt, nowMs),
            ayt = countdownTo(settings.aytExamAt, nowMs),
            datesConfirmed = settings.datesConfirmed,
            yearLabel = settings.yksYearLabel,
            lastTytQuarters = tytTrend.lastOrNull()?.totalNetQuarters,
            lastAytQuarters = aytTrend.lastOrNull()?.totalNetQuarters,
            tytSpark = tytTrend.takeLast(10).map { it.totalNetQuarters / 4f },
            aytSpark = aytTrend.takeLast(10).map { it.totalNetQuarters / 4f },
            todayTasks = weekTasks.filter { it.dayOfWeek == todayIso },
            weekDone = weekTasks.count { it.isDone },
            weekTotal = weekTasks.size,
            daysSinceBackup = settings.lastBackupAt?.let { (nowMs - it) / 86_400_000L },
            autoBackupConfigured = settings.backupDirUri != null,
        )
    }
        .combine(streakUseCase.observe()) { state, streak -> state.copy(streak = streak) }
        .combine(streakUseCase.observeDayCounts()) { state, dayCounts ->
            state.copy(
                heatGrid = com.yks2027.tracker.core.model.HeatStripBuckets.grid(
                    dayCounts, clock.today(), weeks = 8,
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private val _pendingCapture = MutableStateFlow<PlanTaskEntity?>(null)
    val pendingCapture = _pendingCapture.asStateFlow()

    fun toggleTask(task: PlanTaskEntity) {
        // PRD §6.4: same honest-count capture as the planner.
        if (!task.isDone && task.targetQuestions != null) {
            _pendingCapture.value = task
            return
        }
        viewModelScope.launch {
            val done = !task.isDone
            planDao.setDone(
                id = task.id,
                done = done,
                completedAt = if (done) clock.now().toEpochMilli() else null,
                solved = null,
            )
        }
    }

    fun confirmCapture(task: PlanTaskEntity, solved: Int?) {
        _pendingCapture.value = null
        viewModelScope.launch {
            planDao.setDone(task.id, done = true, completedAt = clock.now().toEpochMilli(), solved = solved)
        }
    }

    fun dismissCapture() {
        _pendingCapture.value = null
    }

    private fun countdownTo(target: Long, nowMs: Long): Countdown {
        var s = (target - nowMs) / 1000
        if (s <= 0) return Countdown(0, 0, 0, 0, past = true)
        val days = s / 86_400; s %= 86_400
        val hours = s / 3_600; s %= 3_600
        return Countdown(days, hours, s / 60, s % 60, past = false)
    }
}

@Composable
fun DashboardScreen(
    onAddExam: () -> Unit,
    onStartTimer: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pendingCapture by viewModel.pendingCapture.collectAsStateWithLifecycle()
    val expanded = isExpandedWidth()

    pendingCapture?.let { task ->
        SolvedCaptureDialog(
            task = task,
            onConfirm = { solved -> viewModel.confirmCapture(task, solved) },
            onDismiss = viewModel::dismissCapture,
        )
    }

    val countdowns: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CountdownCard("TYT", ui.tyt, ui.datesConfirmed)
            CountdownCard("AYT", ui.ayt, ui.datesConfirmed)
        }
    }
    val rightPane: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiRow(ui)
            TodayTasksCard(ui.todayTasks, onToggle = viewModel::toggleTask)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddExam) { Text("Deneme Ekle") }
                OutlinedButton(onClick = onStartTimer) { Text("Sayaç Başlat") }
            }
            BackupNudge(ui)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ana Sayfa",
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                ui.yearLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { countdowns() }
                Column(Modifier.weight(1f)) { rightPane() }
            }
        } else {
            countdowns()
            rightPane()
        }
    }
}

@Composable
private fun CountdownCard(title: String, c: Countdown, confirmed: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$title'ye kalan", style = MaterialTheme.typography.titleMedium)
            if (c.past) {
                Text("Sınav günü geçti", style = MaterialTheme.typography.headlineSmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountdownUnit(c.days.toString(), "gün")
                    CountdownUnit(c.hours.toString().padStart(2, '0'), "saat")
                    CountdownUnit(c.minutes.toString().padStart(2, '0'), "dk")
                    CountdownUnit(c.seconds.toString().padStart(2, '0'), "sn")
                }
            }
            if (!confirmed) {
                Text(
                    "ÖSYM takvimi henüz açıklanmadı — tahmini tarih",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CountdownUnit(value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                value,
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(unit, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BackupNudge(ui: DashboardUiState) {
    val text = when {
        ui.daysSinceBackup == null && !ui.autoBackupConfigured ->
            "Henüz yedek alınmadı — Ayarlar'dan dışa aktarabilir veya otomatik yedek klasörü seçebilirsin."
        ui.daysSinceBackup == null -> "Otomatik yedekleme kurulu; ilk yedek uygulama açılışında alınır."
        ui.daysSinceBackup >= 7 -> "Son yedek ${ui.daysSinceBackup} gün önce — yenilemek iyi olur."
        else -> "Son yedek ${ui.daysSinceBackup} gün önce."
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun KpiRow(ui: DashboardUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KpiCard(
            "Son TYT Neti",
            ui.lastTytQuarters?.let(NetCalculator::format) ?: "—",
            Modifier.weight(1f),
        ) { SparkOrHint(ui.tytSpark) }
        KpiCard(
            "Son AYT Neti",
            ui.lastAytQuarters?.let(NetCalculator::format) ?: "—",
            Modifier.weight(1f),
        ) { SparkOrHint(ui.aytSpark) }
        KpiCard("Seri", if (ui.streak > 0) "${ui.streak} gün" else "—", Modifier.weight(1f)) {
            // v1.2 görsel paso — 8-week activity strip right under the streak number.
            if (ui.heatGrid.isNotEmpty()) {
                com.yks2027.tracker.core.ui.charts.HeatStrip(
                    grid = ui.heatGrid,
                    modifier = Modifier.padding(top = 6.dp),
                    cell = 8.dp,
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(Modifier.weight(1f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.yks2027.tracker.core.ui.charts.ProgressRing(
                    progress = if (ui.weekTotal == 0) 0f else ui.weekDone.toFloat() / ui.weekTotal,
                    modifier = Modifier.size(56.dp),
                    stroke = 6.dp,
                ) {
                    Text(
                        if (ui.weekTotal == 0) "—" else "%${ui.weekDone * 100 / ui.weekTotal}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        "Haftalık plan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (ui.weekTotal == 0) "Görev yok" else "${ui.weekDone}/${ui.weekTotal} görev",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SparkOrHint(values: List<Float>) {
    if (values.size >= 2) {
        com.yks2027.tracker.core.ui.charts.Sparkline(
            values = values,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(24.dp),
        )
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            extra()
        }
    }
}

@Composable
private fun TodayTasksCard(tasks: List<PlanTaskEntity>, onToggle: (PlanTaskEntity) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Bugünün görevleri", style = MaterialTheme.typography.titleMedium)
            if (tasks.isEmpty()) {
                Text(
                    "Bugün için görev yok",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            tasks.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.isDone, onCheckedChange = { onToggle(task) })
                    Surface(
                        color = task.category.containerColor(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            task.category.label,
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = task.category.contentColor(),
                        )
                    }
                    Text(
                        task.topic,
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}
