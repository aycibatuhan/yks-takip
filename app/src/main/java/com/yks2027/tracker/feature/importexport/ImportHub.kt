package com.yks2027.tracker.feature.importexport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.ai.AiException
import com.yks2027.tracker.core.ai.ExamExtractor
import com.yks2027.tracker.core.ai.ExtractSource
import com.yks2027.tracker.core.backup.CsvCodec
import com.yks2027.tracker.core.database.ExamDao
import com.yks2027.tracker.core.model.NetCalculator
import com.yks2027.tracker.core.time.IstanbulClock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.2 — İçe Aktar hub (Ayarlar + Denemeler'den erişilir): CSV import with a
 * human-confirm preview, and AI extraction (image/PDF/pasted text) that opens the
 * ExamEntry form pre-filled. NOTHING here writes to the DB without the preview/confirm
 * step; the replace-only JSON restore stays untouched in Ayarlar.
 */

data class CsvPreviewRow(
    val exam: CsvCodec.ParsedExam,
    val duplicate: Boolean,
    val selected: Boolean,
)

data class ImportHubState(
    val busy: Boolean = false,
    val busyLabel: String = "",
    val csvRows: List<CsvPreviewRow> = emptyList(),
    val csvErrors: List<String> = emptyList(),
    val aiWarnings: List<String> = emptyList(),
    val error: String? = null,
    /** Set when AI extraction finished and the entry form should open. */
    val openPrefill: Boolean = false,
)

@HiltViewModel
class ImportHubViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val examDao: ExamDao,
    private val extractor: ExamExtractor,
    private val prefillHolder: ExamPrefillHolder,
    private val clock: IstanbulClock,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportHubState())
    val state = _state.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    fun consumeSnackbar() {
        _snackbar.value = null
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun consumedPrefillNavigation() {
        _state.value = _state.value.copy(openPrefill = false)
    }

    // --- CSV ---

    fun loadCsv(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = "CSV okunuyor…", error = null)
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Dosya okunamadı")
                }
                val parsed = CsvCodec.parse(text)
                val rows = parsed.exams.map { exam ->
                    val inDb = examDao.countByKindAndDay(exam.kind, exam.takenAtDay, excludeId = -1) > 0
                    val inFile = parsed.exams.count {
                        it !== exam && it.kind == exam.kind && it.takenAtDay == exam.takenAtDay
                    } > 0
                    val duplicate = inDb || inFile
                    // Duplicates arrive UNCHECKED — importing them is an explicit choice.
                    CsvPreviewRow(exam, duplicate, selected = !duplicate)
                }
                parsed to rows
            }
            result.onSuccess { (parsed, rows) ->
                _state.value = _state.value.copy(
                    busy = false,
                    csvRows = rows,
                    csvErrors = parsed.errors,
                    error = if (rows.isEmpty() && parsed.errors.isNotEmpty()) {
                        "CSV'den deneme çıkarılamadı — satır hatalarına bak."
                    } else if (rows.isEmpty()) {
                        "CSV'de deneme satırı yok."
                    } else {
                        null
                    },
                )
            }.onFailure {
                _state.value = _state.value.copy(busy = false, error = "CSV okunamadı: ${it.message?.take(200)}")
            }
        }
    }

    fun toggleRow(index: Int) {
        val rows = _state.value.csvRows.toMutableList()
        val row = rows.getOrNull(index) ?: return
        rows[index] = row.copy(selected = !row.selected)
        _state.value = _state.value.copy(csvRows = rows)
    }

    fun clearCsv() {
        _state.value = _state.value.copy(csvRows = emptyList(), csvErrors = emptyList())
    }

    /** ADDITIVE insert — the whole point of this path vs. the replace-only restore. */
    fun importSelected() {
        val selected = _state.value.csvRows.filter { it.selected }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = "İçe aktarılıyor…")
            val now = clock.now().toEpochMilli()
            var imported = 0
            selected.forEach { row ->
                val (exam, sections) = CsvCodec.toEntities(row.exam, now)
                runCatching { examDao.upsertExamWithSections(exam, sections) }
                    .onSuccess { imported++ }
            }
            _state.value = _state.value.copy(busy = false, csvRows = emptyList(), csvErrors = emptyList())
            _snackbar.value = "$imported deneme eklendi"
        }
    }

    // --- AI extraction ---

    fun extractFromImage(uri: Uri) = extract("Görsel Claude'a gönderiliyor…") {
        val bytes = readAll(uri)
        val scaled = withContext(Dispatchers.Default) { downscaleJpeg(bytes) }
        extractor.extract(ExtractSource.Image(scaled, isPng = false))
    }

    fun extractFromPdf(uri: Uri) = extract("PDF Claude'a gönderiliyor…") {
        val bytes = readAll(uri)
        if (bytes.size > 20 * 1024 * 1024) throw AiException("PDF çok büyük (>20MB) — tek sayfayı görsel olarak dene.")
        extractor.extract(ExtractSource.Pdf(bytes))
    }

    fun extractFromText(raw: String) = extract("Metin Claude'a gönderiliyor…") {
        if (raw.isBlank()) throw AiException("Metin boş.")
        extractor.extract(ExtractSource.Text(raw.take(20_000)))
    }

    private fun extract(label: String, block: suspend () -> com.yks2027.tracker.core.ai.ExtractedExam) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, busyLabel = label, error = null, aiWarnings = emptyList())
            runCatching { block() }
                .onSuccess { extracted ->
                    prefillHolder.set(extracted)
                    _state.value = _state.value.copy(
                        busy = false,
                        aiWarnings = extracted.warnings,
                        openPrefill = true,
                    )
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.value = _state.value.copy(
                        busy = false,
                        error = (error as? AiException)?.message
                            ?: "Beklenmeyen hata: ${error.message?.take(200)}",
                    )
                }
        }
    }

    private suspend fun readAll(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw AiException("Dosya okunamadı.")
    }

    /** Longest side ≤ 1568px, JPEG 85 — the sweet spot for vision token cost. */
    private fun downscaleJpeg(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) throw AiException("Görsel çözümlenemedi.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1568) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw AiException("Görsel çözümlenemedi.")
        val longest = maxOf(bitmap.width, bitmap.height)
        val finalBitmap = if (longest > 1568) {
            val scale = 1568f / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }
}

private val previewDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("tr"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHubScreen(
    onBack: () -> Unit,
    onOpenPrefilledEntry: () -> Unit,
    viewModel: ImportHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pasteDialogOpen by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::loadCsv)
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::extractFromImage)
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::extractFromPdf)
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    LaunchedEffect(state.openPrefill) {
        if (state.openPrefill) {
            viewModel.consumedPrefillNavigation()
            onOpenPrefilledEntry()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İçe Aktar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(state.busyLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.error?.let { error ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            error,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = viewModel::dismissError) { Text("Kapat") }
                    }
                }
            }

            if (state.csvRows.isNotEmpty()) {
                CsvPreviewCard(
                    rows = state.csvRows,
                    errors = state.csvErrors,
                    onToggle = viewModel::toggleRow,
                    onImport = viewModel::importSelected,
                    onCancel = viewModel::clearCsv,
                    busy = state.busy,
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CSV'den", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Deneme sonuçlarını tabloyla topluca ekle. Dosya UTF-8, ayraç ';' " +
                                "ve başlık satırı şu olmalı:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            CsvCodec.HEADER,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "Örnek satır: 2026-08-22;TYT_FULL;Deneme 8;Limit;TYT_MATEMATIK;40;28;8;4;26,00\n" +
                                "Tam deneme = her ders bir satır (aynı tarih+tur+ad). bos ve net " +
                                "sütunları içe aktarırken yok sayılır (yeniden hesaplanır). " +
                                "Denemeler ekranındaki \"CSV dışa aktar\" aynı biçimi üretir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = {
                            csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))
                        }) { Text("CSV Dosyası Seç") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AI ile (görsel / PDF / metin)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Deneme sonuç karnesinin fotoğrafını, PDF'ini ya da kopyaladığın metni " +
                            "Claude okur; sonuç ONAY İÇİN doldurulmuş deneme formunda açılır — " +
                            "hiçbir şey otomatik kaydedilmez. Şimdilik yalnız Anthropic " +
                            "profilleriyle çalışır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { imageLauncher.launch(arrayOf("image/*")) },
                            enabled = !state.busy,
                        ) { Text("Görsel Seç") }
                        OutlinedButton(
                            onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                            enabled = !state.busy,
                        ) { Text("PDF Seç") }
                        OutlinedButton(
                            onClick = { pasteDialogOpen = true },
                            enabled = !state.busy,
                        ) { Text("Metin Yapıştır") }
                    }
                }
            }

            Text(
                "Not: JSON yedeği geri yükleme (tümünü DEĞİŞTİRİR) Ayarlar → Yedekleme altında; " +
                    "buradaki yollar mevcut verilerin üzerine EKLER.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pasteDialogOpen) {
        var pasted by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasteDialogOpen = false },
            title = { Text("Sonuç metnini yapıştır") },
            text = {
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("Karne / sonuç metni") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pasteDialogOpen = false
                        viewModel.extractFromText(pasted)
                    },
                    enabled = pasted.isNotBlank(),
                ) { Text("Claude'a Gönder") }
            },
            dismissButton = {
                TextButton(onClick = { pasteDialogOpen = false }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun CsvPreviewCard(
    rows: List<CsvPreviewRow>,
    errors: List<String>,
    onToggle: (Int) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Önizleme — onaylananlar eklenecek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rows.forEachIndexed { index, row ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = row.selected, onCheckedChange = { onToggle(index) })
                    Column(Modifier.weight(1f)) {
                        val total = row.exam.sections.sumOf {
                            NetCalculator.netQuarters(it.correct, it.wrong)
                        }
                        Text(
                            buildString {
                                append(LocalDate.ofEpochDay(row.exam.takenAtDay).format(previewDate))
                                append(" · ").append(row.exam.kind.label)
                                row.exam.name?.let { append(" · ").append(it) }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${row.exam.sections.size} ders · net ${NetCalculator.format(total)}" +
                                if (row.duplicate) "  ⚠ aynı gün+tür zaten var" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.duplicate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (errors.isNotEmpty()) {
                Text(
                    "Atlanan satırlar:\n" + errors.take(8).joinToString("\n") + if (errors.size > 8) "\n…" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImport,
                    enabled = !busy && rows.any { it.selected },
                ) { Text("İçeri Aktar (${rows.count { it.selected }})") }
                TextButton(onClick = onCancel) { Text("Vazgeç") }
            }
        }
    }
}
