package com.yks2027.tracker.feature.timer

import com.yks2027.tracker.core.platform.TimerCompletionScheduler
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.database.FocusDao
import com.yks2027.tracker.core.database.FocusSessionEntity
import com.yks2027.tracker.core.datastore.TimerLogic
import com.yks2027.tracker.core.datastore.TimerPhase
import com.yks2027.tracker.core.datastore.TimerStateRepository
import com.yks2027.tracker.core.model.PlannerCategory
import com.yks2027.tracker.core.time.ISTANBUL
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val mode: com.yks2027.tracker.core.datastore.TimerMode = com.yks2027.tracker.core.datastore.TimerMode.COUNTDOWN,
    val remainingMs: Long = 0,
    /** v1.2 Kronometre — elapsed active time (net of pauses). */
    val elapsedMs: Long = 0,
    val plannedMin: Int = 25,
    val recent: List<FocusSessionEntity> = emptyList(),
    val isBreak: Boolean = false,
    /** Auto-break setting (minutes; 0 = off) and whether to suggest one right now. */
    val autoBreakMin: Int = 0,
    val suggestBreak: Boolean = false,
)

class TimerViewModel constructor(
    private val timerStateRepository: TimerStateRepository,
    private val timerController: TimerController,
    private val settingsRepository: com.yks2027.tracker.core.datastore.SettingsRepository,
    focusDao: FocusDao,
) : ViewModel() {

    val plannedMinInput = MutableStateFlow("25")
    val selectedCategory = MutableStateFlow<PlannerCategory?>(null)

    private val tick = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(250)
        }
    }

    /** IDLE mode selection (persisted state carries the mode once running). */
    val selectedMode = MutableStateFlow(com.yks2027.tracker.core.datastore.TimerMode.COUNTDOWN)

    val ui = combine(
        timerStateRepository.state,
        tick,
        focusDao.observeRecent(10),
        settingsRepository.settings,
        selectedMode,
    ) { s, now, recent, settings, idleMode ->
        val lastSession = recent.firstOrNull()
        TimerUiState(
            phase = s.phase,
            mode = if (s.phase == TimerPhase.IDLE) idleMode else s.mode,
            remainingMs = TimerLogic.remainingAt(s, now),
            elapsedMs = TimerLogic.elapsedAt(s, now),
            plannedMin = s.plannedMin,
            recent = recent,
            isBreak = s.isBreak,
            autoBreakMin = settings.autoBreakMin,
            // Suggest a break right after a completed study session (PRD §12 M3).
            suggestBreak = s.phase == TimerPhase.IDLE &&
                settings.autoBreakMin > 0 &&
                lastSession?.completed == true &&
                now - lastSession.endedAt < 2 * 60_000L,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    fun setMode(mode: com.yks2027.tracker.core.datastore.TimerMode) {
        selectedMode.value = mode
    }

    fun finishStopwatch() = viewModelScope.launch { timerController.finishStopwatch() }

    fun setAutoBreak(minutes: Int) {
        viewModelScope.launch { settingsRepository.setAutoBreakMin(minutes) }
    }

    fun startBreak() {
        val minutes = ui.value.autoBreakMin.takeIf { it > 0 } ?: return
        viewModelScope.launch { timerController.start(minutes, category = null, isBreak = true) }
    }

    init {
        // In-app fallback: if the exact alarm couldn't fire (permission edge cases),
        // finalize from the UI tick. finalizeCompleted() is guarded, so double calls are safe.
        viewModelScope.launch {
            combine(timerStateRepository.state, tick) { s, now -> s to now }
                .collect { (s, now) ->
                    if (s.phase == TimerPhase.RUNNING && s.endAt != null && now >= s.endAt) {
                        timerController.finalizeCompleted()
                    }
                }
        }
    }

    fun setPlannedMin(raw: String) {
        plannedMinInput.value = raw.filter { it.isDigit() }.take(3)
    }

    fun setCategory(category: PlannerCategory?) {
        selectedCategory.value = category
    }

    fun start() {
        if (selectedMode.value == com.yks2027.tracker.core.datastore.TimerMode.STOPWATCH) {
            viewModelScope.launch { timerController.startStopwatch(selectedCategory.value) }
            return
        }
        val minutes = plannedMinInput.value.toIntOrNull() ?: return
        viewModelScope.launch { timerController.start(minutes.coerceIn(1, 300), selectedCategory.value) }
    }

    fun pause() = viewModelScope.launch { timerController.pause() }
    fun resume() = viewModelScope.launch { timerController.resume() }
    fun reset() = viewModelScope.launch { timerController.reset() }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

private val sessionDate = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("tr"))

@Composable
fun TimerScreen(viewModel: TimerViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val plannedInput by viewModel.plannedMinInput.collectAsStateWithLifecycle()
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    // PRD §7.2: request POST_NOTIFICATIONS contextually on first start (Android 13+); a
    // denial still starts the timer. Desktop: no permission concept — starts immediately.
    val scheduler: TimerCompletionScheduler = koinInject()
    fun onStartClick() = scheduler.ensureNotificationPermission { viewModel.start() }

    val isStopwatch = ui.mode == com.yks2027.tracker.core.datastore.TimerMode.STOPWATCH
    val displayMs = when {
        isStopwatch -> if (ui.phase == TimerPhase.IDLE) 0L else ui.elapsedMs
        ui.phase == TimerPhase.IDLE -> (plannedInput.toLongOrNull() ?: 0L) * 60_000L
        else -> ui.remainingMs
    }
    // v1.2 görsel paso — dial: countdown drains toward zero; stopwatch sweeps a full
    // revolution per hour (a clock, not fake progress).
    val ringProgress = when {
        ui.phase == TimerPhase.IDLE -> 1f
        isStopwatch -> (ui.elapsedMs % 3_600_000L) / 3_600_000f
        else -> {
            val total = ui.plannedMin * 60_000f
            if (total > 0f) ui.remainingMs / total else 0f
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Sayaç", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (ui.phase == TimerPhase.IDLE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isStopwatch,
                    onClick = { viewModel.setMode(com.yks2027.tracker.core.datastore.TimerMode.COUNTDOWN) },
                    label = { Text("Geri sayım") },
                )
                FilterChip(
                    selected = isStopwatch,
                    onClick = { viewModel.setMode(com.yks2027.tracker.core.datastore.TimerMode.STOPWATCH) },
                    label = { Text("Kronometre") },
                )
            }
        }

        com.yks2027.tracker.core.ui.charts.ProgressRing(
            progress = ringProgress,
            modifier = Modifier.size(240.dp),
            stroke = 10.dp,
            color = when (ui.phase) {
                TimerPhase.RUNNING -> MaterialTheme.colorScheme.primary
                TimerPhase.PAUSED -> MaterialTheme.colorScheme.outline
                TimerPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatMs(displayMs),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp),
                    fontFamily = FontFamily.Monospace,
                    color = when (ui.phase) {
                        TimerPhase.RUNNING -> MaterialTheme.colorScheme.primary
                        TimerPhase.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
                        TimerPhase.IDLE -> MaterialTheme.colorScheme.onSurface
                    },
                )
                if (isStopwatch && ui.phase != TimerPhase.IDLE) {
                    Text(
                        "serbest çalışma",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (ui.isBreak && ui.phase != TimerPhase.IDLE) {
            Text(
                "MOLA",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
        }

        if (ui.suggestBreak) {
            Card {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Güzel oturumdu! Kısa bir mola?", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = viewModel::startBreak) { Text("${ui.autoBreakMin} dk mola") }
                }
            }
        }

        if (ui.phase == TimerPhase.IDLE) {
            if (!isStopwatch) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = plannedInput,
                        onValueChange = viewModel::setPlannedMin,
                        label = { Text("Dakika (1–300)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 45, 60, 90).forEach { preset ->
                        FilterChip(
                            selected = plannedInput == preset.toString(),
                            onClick = { viewModel.setPlannedMin(preset.toString()) },
                            label = { Text("$preset dk") },
                        )
                    }
                }
            } else {
                Text(
                    "Süre sınırı yok — durdurduğunda geçen süre oturum olarak kaydedilir (≥1 dk).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CategoryPicker(selected = category, onSelect = viewModel::setCategory)
            if (!isStopwatch) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10).forEach { minutes ->
                        FilterChip(
                            selected = ui.autoBreakMin == minutes,
                            onClick = { viewModel.setAutoBreak(minutes) },
                            label = { Text(if (minutes == 0) "Mola: Kapalı" else "Mola: $minutes dk") },
                        )
                    }
                }
            }
        } else {
            category?.let {
                Text("Kategori: ${it.label}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (ui.phase) {
                TimerPhase.IDLE -> {
                    Button(
                        onClick = ::onStartClick,
                        enabled = isStopwatch || (plannedInput.toIntOrNull() ?: 0) in 1..300,
                    ) {
                        Text("BAŞLAT")
                    }
                }
                TimerPhase.RUNNING -> {
                    Button(onClick = { viewModel.pause() }) { Text("DURAKLAT") }
                    if (isStopwatch) {
                        Button(onClick = { viewModel.finishStopwatch() }) { Text("BİTİR & KAYDET") }
                    }
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("SIFIRLA") }
                }
                TimerPhase.PAUSED -> {
                    Button(onClick = { viewModel.resume() }) { Text("DEVAM") }
                    if (isStopwatch) {
                        Button(onClick = { viewModel.finishStopwatch() }) { Text("BİTİR & KAYDET") }
                    }
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("SIFIRLA") }
                }
            }
        }

        RecentSessions(ui.recent)
    }
}

@Composable
private fun CategoryPicker(selected: PlannerCategory?, onSelect: (PlannerCategory?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(selected?.label ?: "Kategori seç (opsiyonel)")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Kategorisiz") }, onClick = {
                onSelect(null)
                open = false
            })
            PlannerCategory.entries.forEach { c ->
                DropdownMenuItem(text = { Text(c.label) }, onClick = {
                    onSelect(c)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun RecentSessions(sessions: List<FocusSessionEntity>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Son oturumlar", style = MaterialTheme.typography.titleMedium)
            if (sessions.isEmpty()) {
                Text(
                    "Henüz kayıtlı oturum yok (≥1 dk süren oturumlar kaydedilir)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sessions.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Instant.ofEpochMilli(s.startedAt).atZone(ISTANBUL).format(sessionDate),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            (s.category?.label ?: "Kategorisiz") +
                                if (s.plannedMin == 0) " · kronometre" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${s.activeMs / 60_000} dk",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (s.completed) "  ✓" else "  yarım",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.completed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
