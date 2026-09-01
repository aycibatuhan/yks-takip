package com.yks2027.tracker.feature.exams

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.database.ExamWithSections
import com.yks2027.tracker.core.model.ExamKind
import com.yks2027.tracker.core.model.NetCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
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

@HiltViewModel
class ExamListViewModel @Inject constructor(
    private val examDao: ExamDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val clock: com.yks2027.tracker.core.time.IstanbulClock,
) : ViewModel() {

    private val filter = MutableStateFlow(ExamFilter.ALL)
    val selectedFilter = filter.asStateFlow()

    val exams = combine(examDao.observeAll(), filter) { all, f ->
        all.filter { f.matches(it.exam.examKind) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    /** One-shot content:// uri for the share sheet (FileProvider over cacheDir). */
    private val _shareUri = MutableStateFlow<android.net.Uri?>(null)
    val shareUri = _shareUri.asStateFlow()

    private var lastDeleted: ExamWithSections? = null

    fun setFilter(f: ExamFilter) {
        filter.value = f
    }

    fun suggestedCsvName(): String =
        "yks_denemeler_${com.yks2027.tracker.core.time.dateOf(clock.now())}.csv"

    /** v1.2 — CSV dışa aktarma, SAF hedefine (documented format, see CsvCodec). */
    fun exportCsvTo(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val csv = com.yks2027.tracker.core.backup.CsvCodec.export(examDao.allOnce())
                appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("Dosya yazılamadı")
            }
                .onSuccess { _snackbar.value = "CSV kaydedildi" }
                .onFailure { _snackbar.value = "CSV dışa aktarılamadı: ${it.message?.take(120)}" }
        }
    }

    /** v1.2 — CSV'yi paylaşım sayfasına ver (WhatsApp/Drive/e-posta…). */
    fun prepareCsvShare() {
        viewModelScope.launch {
            runCatching {
                val csv = com.yks2027.tracker.core.backup.CsvCodec.export(examDao.allOnce())
                val dir = java.io.File(appContext.cacheDir, "share").apply { mkdirs() }
                val file = java.io.File(dir, suggestedCsvName())
                file.writeText(csv, Charsets.UTF_8)
                androidx.core.content.FileProvider.getUriForFile(
                    appContext, "com.yks2027.tracker.fileprovider", file,
                )
            }
                .onSuccess { _shareUri.value = it }
                .onFailure { _snackbar.value = "CSV hazırlanamadı: ${it.message?.take(120)}" }
        }
    }

    fun consumeShareUri() {
        _shareUri.value = null
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
    viewModel: ExamListViewModel = hiltViewModel(),
) {
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val shareUri by viewModel.shareUri.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val csvSaveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsvTo) }

    LaunchedEffect(shareUri) {
        val uri = shareUri ?: return@LaunchedEffect
        viewModel.consumeShareUri()
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Deneme CSV'sini paylaş"))
    }

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
                                    csvSaveLauncher.launch(viewModel.suggestedCsvName())
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("CSV paylaş") },
                                onClick = {
                                    exportMenuOpen = false
                                    viewModel.prepareCsvShare()
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
