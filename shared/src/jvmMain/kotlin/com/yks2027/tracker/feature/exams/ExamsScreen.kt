package com.yks2027.tracker.feature.exams

import com.yks2027.tracker.core.platform.PlatformFiles
import com.yks2027.tracker.core.platform.ShareService
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.ExamWithSections
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.NetCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ExamFilter(val label: String) {
    ALL("Tümü"), TYT("TYT"), AYT("AYT"), BRANS("Branş");

    fun matches(kind: ExamKind): Boolean = when (this) {
        ALL -> true
        TYT -> kind == ExamKind.TYT_FULL
        AYT -> kind == ExamKind.AYT_SAY_FULL
        BRANS -> kind == ExamKind.BRANS_TYT || kind == ExamKind.BRANS_AYT
    }
}

class ExamListViewModel(
    private val examDao: ExamDao,
    private val clock: com.yks2027.tracker.core.time.IstanbulClock,
    private val files: PlatformFiles,
    private val share: ShareService,
) : ViewModel() {

    private val filter = MutableStateFlow(ExamFilter.ALL)
    val selectedFilter = filter.asStateFlow()

    val exams = combine(examDao.observeAll(), filter) { all, f ->
        all.filter { f.matches(it.exam.examKind) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    private var lastDeleted: ExamWithSections? = null

    fun setFilter(f: ExamFilter) {
        filter.value = f
    }

    fun suggestedCsvName(): String =
        "yks_denemeler_${com.yks2027.tracker.core.time.dateOf(clock.now())}.csv"

    /** v1.2/v2.0 — CSV dışa aktarma via the platform save dialog (documented format, see CsvCodec). */
    fun exportCsv() {
        viewModelScope.launch {
            runCatching {
                val csv = com.yks2027.tracker.core.backup.CsvCodec.export(examDao.allOnce())
                files.saveFile(suggestedCsvName(), "csv", "text/csv", csv.encodeToByteArray())
            }
                .onSuccess { where -> if (where != null) _snackbar.value = "CSV kaydedildi: $where" }
                .onFailure { _snackbar.value = "CSV dışa aktarılamadı: ${it.message?.take(120)}" }
        }
    }

    /** Android: share sheet (WhatsApp/Drive/e-posta…). Desktop: save dialog + clipboard. */
    fun shareCsv() {
        viewModelScope.launch {
            runCatching {
                val csv = com.yks2027.tracker.core.backup.CsvCodec.export(examDao.allOnce())
                share.shareFile(suggestedCsvName(), "text/csv", csv.encodeToByteArray(), "Deneme CSV'sini paylaş")
            }.onFailure { _snackbar.value = "CSV hazırlanamadı: ${it.message?.take(120)}" }
        }
    }

    fun delete(item: ExamWithSections) {
        viewModelScope.launch {
            lastDeleted = item
            examDao.deleteById(item.exam.id)
            _snackbar.value = "Deneme silindi"
        }
    }

    fun undoDelete() {
        val item = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            examDao.upsertExamWithSections(item.exam, item.sections)
        }
    }

    fun consumeSnackbar() {
        _snackbar.value = null
    }
}

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("tr"))

/** PRD §10.1 — Denemeler destination: Liste + Analiz tabs. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenImportHub: () -> Unit = {},
    viewModel: ExamListViewModel = koinViewModel(),
) {
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Geri Al")
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        viewModel.consumeSnackbar()
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Denemeler") },
                actions = {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { exportMenuOpen = true }) {
                            Icon(Icons.Outlined.ImportExport, contentDescription = "İçe/Dışa aktar")
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("CSV dışa aktar…") },
                                onClick = {
                                    exportMenuOpen = false
                                    viewModel.exportCsv()
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("CSV paylaş") },
                                onClick = {
                                    exportMenuOpen = false
                                    viewModel.shareCsv()
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("İçe aktar (CSV / AI)…") },
                                onClick = {
                                    exportMenuOpen = false
                                    onOpenImportHub()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = "Deneme Ekle")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Liste") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Analiz") })
            }
            if (tab == 0) {
                ExamList(
                    exams = exams,
                    selectedFilter = selectedFilter,
                    onFilter = viewModel::setFilter,
                    onEdit = onEdit,
                    onDelete = viewModel::delete,
                )
            } else {
                AnalyticsTab()
            }
        }
    }
}

@Composable
private fun ExamList(
    exams: List<ExamWithSections>,
    selectedFilter: ExamFilter,
    onFilter: (ExamFilter) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (ExamWithSections) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            ExamFilter.entries.forEach { f ->
                FilterChip(
                    selected = f == selectedFilter,
                    onClick = { onFilter(f) },
                    label = { Text(f.label) },
                )
            }
        }
        if (exams.isEmpty()) {
            Text(
                "Henüz deneme yok — sağ alttan ekle",
                Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(exams, key = { it.exam.id }) { item ->
                ExamRow(item, onEdit = { onEdit(item.exam.id) }, onDelete = { onDelete(item) })
            }
        }
    }
}

@Composable
private fun ExamRow(item: ExamWithSections, onEdit: () -> Unit, onDelete: () -> Unit) {
    val totalQuarters = item.sections.sumOf { NetCalculator.netQuarters(it.correctCount, it.wrongCount) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.exam.name?.takeIf { it.isNotBlank() } ?: item.exam.examKind.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(
                        item.exam.examKind.label,
                        item.sections.singleOrNull()?.subject?.label
                            ?.takeIf { !item.exam.examKind.isFull },
                        item.exam.publisher?.takeIf { it.isNotBlank() },
                        LocalDate.ofEpochDay(item.exam.takenAtDay).format(dateFormat),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                NetCalculator.format(totalQuarters),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (totalQuarters < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Düzenle") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Sil") }
        }
    }
}
