package com.yks2027.tracker.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.outlined.History
import com.yks2027.tracker.core.database.CategoryActiveMs
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.PlanDao
import com.yks2027.tracker.core.database.PlanTaskEntity
import com.yks2027.tracker.core.model.PlannerCategory
import com.yks2027.tracker.core.time.ISTANBUL
import com.yks2027.tracker.core.time.IstanbulClock
import com.yks2027.tracker.core.ui.accent
import com.yks2027.tracker.core.ui.containerColor
import com.yks2027.tracker.core.ui.contentColor
import com.yks2027.tracker.core.ui.isExpandedWidth
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val DAY_NAMES = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val planDao: PlanDao,
    focusDao: FocusDao,
    private val rollover: WeekRolloverUseCase,
    private val clock: IstanbulClock,
) : ViewModel() {

    private val _pendingCapture = MutableStateFlow<PlanTaskEntity?>(null)
    val pendingCapture = _pendingCapture.asStateFlow()

    private val weekStart = MutableStateFlow<Long?>(null)
    val currentWeekStart = weekStart.asStateFlow()

    private val _copyableWeek = MutableStateFlow<Long?>(null)
    val copyableWeek = _copyableWeek.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    val todayIso: Int get() = clock.today().dayOfWeek.value

    private var lastDeletedTask: PlanTaskEntity? = null
    private var lastClearedTasks: List<PlanTaskEntity> = emptyList()

    val tasks = weekStart
        .flatMapLatest { week -> if (week == null) emptyFlow() else planDao.observeTasks(week) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Weekly study minutes per category, from Module D sessions (PRD §6.4). */
    val studyTime = weekStart
        .flatMapLatest { week ->
            if (week == null) {
                emptyFlow()
            } else {
                val from = LocalDate.ofEpochDay(week).atStartOfDay(ISTANBUL).toInstant().toEpochMilli()
                focusDao.observeActiveMsByCategory(from, from + 7L * 86_400_000L)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<CategoryActiveMs>())

    init {
        viewModelScope.launch {
            val result = rollover.ensureCurrentWeek()
            weekStart.value = result.weekStart
            _copyableWeek.value = result.copyableWeek
        }
    }

    fun addTask(dayOfWeek: Int, category: PlannerCategory, topic: String, target: Int?) {
        val week = weekStart.value ?: return
        viewModelScope.launch {
            val order = (planDao.maxOrder(week, dayOfWeek) ?: -1) + 1
            planDao.upsertTask(
                PlanTaskEntity(
                    weekStartDay = week,
                    dayOfWeek = dayOfWeek,
                    category = category,
                    topic = topic.trim(),
                    targetQuestions = target,
                    solvedQuestions = null,
                    isDone = false,
                    completedAt = null,
                    orderIndex = order,
                    createdAt = clock.now().toEpochMilli(),
                ),
            )
        }
    }

    fun toggle(task: PlanTaskEntity) {
        // PRD §6.4: checking off a task with a target asks for the ACTUAL solved count
        // (pre-filled with the target, so honesty costs one tap).
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
            planDao.setDone(
                id = task.id,
                done = true,
                completedAt = clock.now().toEpochMilli(),
                solved = solved,
            )
        }
    }

    fun dismissCapture() {
        _pendingCapture.value = null
    }

    fun deleteTask(task: PlanTaskEntity) {
        viewModelScope.launch {
            lastDeletedTask = task
            planDao.deleteTask(task.id)
            _snackbar.value = "Görev silindi"
        }
    }

    fun resetTicks() {
        val week = weekStart.value ?: return
        viewModelScope.launch {
            planDao.resetTicks(week)
            _snackbar.value = "Tikler sıfırlandı (bu hafta)"
        }
    }

    fun clearAll() {
        val week = weekStart.value ?: return
        viewModelScope.launch {
            lastClearedTasks = planDao.tasksOnce(week)
            planDao.deleteTasksOfWeek(week)
            _snackbar.value = "Bu haftanın görevleri silindi"
        }
    }

    fun undo() {
        viewModelScope.launch {
            lastDeletedTask?.let {
                planDao.insertTasks(listOf(it))
                lastDeletedTask = null
                return@launch
            }
            if (lastClearedTasks.isNotEmpty()) {
                planDao.insertTasks(lastClearedTasks)
                lastClearedTasks = emptyList()
            }
        }
    }

    fun copyLastWeek() {
        val from = _copyableWeek.value ?: return
        val to = weekStart.value ?: return
        viewModelScope.launch {
            rollover.copyWeek(from, to)
            _copyableWeek.value = null
        }
    }

    fun dismissCopyPrompt() {
        _copyableWeek.value = null
    }

    fun consumeSnackbar() {
        _snackbar.value = null
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr"))

@Composable
fun PlannerScreen(
    onOpenHistory: () -> Unit = {},
    viewModel: PlannerViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val weekStart by viewModel.currentWeekStart.collectAsStateWithLifecycle()
    val copyableWeek by viewModel.copyableWeek.collectAsStateWithLifecycle()
    val studyTime by viewModel.studyTime.collectAsStateWithLifecycle()
    val pendingCapture by viewModel.pendingCapture.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }
    var addDialogDay by remember { mutableStateOf<Int?>(null) }
    val expanded = isExpandedWidth()

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Geri Al")
        if (result == SnackbarResult.ActionPerformed) viewModel.undo()
        viewModel.consumeSnackbar()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            HeaderRow(
                weekStart = weekStart,
                onResetTicks = viewModel::resetTicks,
                onClearAll = { showClearConfirm = true },
                onOpenHistory = onOpenHistory,
            )
            StatsRow(tasks)
            StudyTimeRow(studyTime)

            if (copyableWeek != null) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Geçen haftanın planını kopyala?", Modifier.weight(1f))
                        Button(onClick = viewModel::copyLastWeek) { Text("Kopyala") }
                        TextButton(onClick = viewModel::dismissCopyPrompt) { Text("Kapat") }
                    }
                }
            }

            val tasksByDay = tasks.groupBy { it.dayOfWeek }
            if (expanded) {
                Row(
                    Modifier.weight(1f).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..7).forEach { day ->
                        DayColumn(
                            day = day,
                            isToday = day == viewModel.todayIso,
                            tasks = tasksByDay[day].orEmpty(),
                            onToggle = viewModel::toggle,
                            onDelete = viewModel::deleteTask,
                            onAdd = { addDialogDay = day },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                val pagerState = rememberPagerState(
                    initialPage = viewModel.todayIso - 1,
                    pageCount = { 7 },
                )
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).padding(top = 8.dp)) { page ->
                    val day = page + 1
                    DayColumn(
                        day = day,
                        isToday = day == viewModel.todayIso,
                        tasks = tasksByDay[day].orEmpty(),
                        onToggle = viewModel::toggle,
                        onDelete = viewModel::deleteTask,
                        onAdd = { addDialogDay = day },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Tümünü Temizle") },
            text = { Text("Bu haftanın tüm görevleri silinecek. Geçmiş haftalar etkilenmez.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearAll()
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Vazgeç") }
            },
        )
    }

    addDialogDay?.let { day ->
        AddTaskDialog(
            day = day,
            onDismiss = { addDialogDay = null },
            onAdd = { category, topic, target ->
                viewModel.addTask(day, category, topic, target)
                addDialogDay = null
            },
        )
    }

    pendingCapture?.let { task ->
        SolvedCaptureDialog(
            task = task,
            onConfirm = { solved -> viewModel.confirmCapture(task, solved) },
            onDismiss = viewModel::dismissCapture,
        )
    }
}

/** PRD §6.4 — captures the actual solved count at check-off, pre-filled with the target. */
@Composable
fun SolvedCaptureDialog(
    task: PlanTaskEntity,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(task.targetQuestions?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kaç soru çözdün?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${task.category.label} · ${task.topic}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { raw -> input = raw.filter { it.isDigit() }.take(4) },
                    label = { Text("Çözülen soru (hedef: ${task.targetQuestions})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input.toIntOrNull()) }) { Text("Tamam") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}

/** v1.2 görsel paso — study time per category as one stacked bar (color = category). */
@Composable
private fun StudyTimeRow(studyTime: List<CategoryActiveMs>) {
    if (studyTime.isEmpty()) return
    val totalMin = studyTime.sumOf { it.totalMs } / 60_000
    val neutral = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Bu hafta çalışma: $totalMin dk",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        com.yks2027.tracker.core.ui.charts.SegmentedBar(
            segments = studyTime.map { entry ->
                com.yks2027.tracker.core.ui.charts.BarSegment(
                    value = entry.totalMs.toFloat(),
                    color = entry.category?.accent ?: neutral,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            height = 10.dp,
        )
        com.yks2027.tracker.core.ui.charts.SegmentLegend(
            entries = studyTime.map { entry ->
                Triple(
                    entry.category?.label ?: "Kategorisiz",
                    entry.category?.accent ?: neutral,
                    "${entry.totalMs / 60_000} dk",
                )
            },
        )
    }
}

@Composable
private fun HeaderRow(
    weekStart: Long?,
    onResetTicks: () -> Unit,
    onClearAll: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val label = weekStart?.let {
            val start = LocalDate.ofEpochDay(it)
            "${start.format(shortDate)} – ${start.plusDays(6).format(shortDate)}"
        } ?: ""
        Column(Modifier.weight(1f)) {
            Text("Haftalık Plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onOpenHistory) {
            Icon(Icons.Outlined.History, contentDescription = "Geçmiş Haftalar")
        }
        TextButton(onClick = onResetTicks) { Text("Tikleri Sıfırla") }
        TextButton(onClick = onClearAll) { Text("Tümünü Temizle") }
    }
}

@Composable
private fun StatsRow(tasks: List<PlanTaskEntity>) {
    val total = tasks.size
    val done = tasks.count { it.isDone }
    val targetSum = tasks.sumOf { it.targetQuestions ?: 0 }
    val solvedSum = tasks.sumOf { it.solvedQuestions ?: 0 }
    val ratio = if (total == 0) 0f else done.toFloat() / total

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // v1.2 görsel paso — completion ring with the number inside.
        com.yks2027.tracker.core.ui.charts.ProgressRing(
            progress = ratio,
            modifier = Modifier.size(52.dp),
            stroke = 6.dp,
        ) {
            Text(
                if (total == 0) "—" else "%${(ratio * 100).toInt()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
            Text(
                "Tamamlanan: $done/$total   Hedeflenen: $targetSum soru   Çözülen: $solvedSum soru",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayColumn(
    day: Int,
    isToday: Boolean,
    tasks: List<PlanTaskEntity>,
    onToggle: (PlanTaskEntity) -> Unit,
    onDelete: (PlanTaskEntity) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DAY_NAMES[day - 1],
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onAdd) { Text("+") }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tasks.forEach { task ->
                    TaskCard(task, onToggle = { onToggle(task) }, onDelete = { onDelete(task) })
                }
                if (tasks.isEmpty()) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: PlanTaskEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = task.category.containerColor(),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    task.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = task.category.contentColor(),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(task.topic)
                        task.targetQuestions?.let { append(" · $it soru") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (task.isDone) 0.65f else 1f),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    day: Int,
    onDismiss: () -> Unit,
    onAdd: (PlannerCategory, String, Int?) -> Unit,
) {
    var category by remember { mutableStateOf(PlannerCategory.TYT_MAT) }
    var topic by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${DAY_NAMES[day - 1]} — görev ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { menuOpen = true }) {
                        Surface(color = category.accent, shape = MaterialTheme.shapes.extraSmall) {
                            Text("  ", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("  ${category.label}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        PlannerCategory.entries.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.label) },
                                onClick = {
                                    category = c
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Konu / not (örn. Türev Test 3-4)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { raw -> target = raw.filter { it.isDigit() }.take(4) },
                    label = { Text("Hedef soru (opsiyonel)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(category, topic, target.toIntOrNull()) },
                enabled = topic.isNotBlank(),
            ) { Text("Ekle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}
