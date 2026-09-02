package com.yks2027.tracker.feature.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.yks2027.tracker.core.backup.BackupManager
import com.yks2027.tracker.core.datastore.Settings
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.datastore.ThemeMode
import com.yks2027.tracker.core.time.ISTANBUL
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Settings(
            tytExamAt = SettingsRepository.DEFAULT_TYT_EXAM_AT,
            aytExamAt = SettingsRepository.DEFAULT_AYT_EXAM_AT,
            datesConfirmed = false,
            themeMode = ThemeMode.SYSTEM,
            backupDirUri = null,
            lastBackupAt = null,
            activeAiProfileId = null,
            aiShareStats = true,
            autoBreakMin = 0,
        ),
    )

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    fun suggestedBackupName(): String = backupManager.suggestedFileName()

    // Date and time are edited separately; each setter preserves the other component.
    fun setTytDate(date: LocalDate) = viewModelScope.launch {
        val time = epochMsToIstanbulTime(settingsRepository.settings.first().tytExamAt)
        settingsRepository.setTytExamAt(date.atTime(time).atZone(ISTANBUL).toInstant().toEpochMilli())
    }

    fun setAytDate(date: LocalDate) = viewModelScope.launch {
        val time = epochMsToIstanbulTime(settingsRepository.settings.first().aytExamAt)
        settingsRepository.setAytExamAt(date.atTime(time).atZone(ISTANBUL).toInstant().toEpochMilli())
    }

    fun setTytTime(hour: Int, minute: Int) = viewModelScope.launch {
        val date = epochMsToIstanbulDate(settingsRepository.settings.first().tytExamAt)
        settingsRepository.setTytExamAt(date.atTime(LocalTime.of(hour, minute)).atZone(ISTANBUL).toInstant().toEpochMilli())
    }

    fun setAytTime(hour: Int, minute: Int) = viewModelScope.launch {
        val date = epochMsToIstanbulDate(settingsRepository.settings.first().aytExamAt)
        settingsRepository.setAytExamAt(date.atTime(LocalTime.of(hour, minute)).atZone(ISTANBUL).toInstant().toEpochMilli())
    }

    fun setBackupDir(uri: String) = viewModelScope.launch {
        settingsRepository.setBackupDirUri(uri)
        _snackbar.value = "Otomatik yedek klasörü ayarlandı"
        runCatching { backupManager.autoBackupIfDue() }
    }

    fun clearBackupDir() = viewModelScope.launch {
        settingsRepository.setBackupDirUri(null)
        _snackbar.value = "Otomatik yedekleme kapatıldı"
    }

    fun setDatesConfirmed(v: Boolean) = viewModelScope.launch { settingsRepository.setDatesConfirmed(v) }
    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(v) }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { _snackbar.value = "Yedek kaydedildi" }
                .onFailure { _snackbar.value = "Yedekleme başarısız: ${it.message}" }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupManager.importFrom(uri) }
                .onSuccess { _snackbar.value = "Yedek geri yüklendi" }
                .onFailure { _snackbar.value = "Geri yükleme başarısız: ${it.message}" }
        }
    }

    fun showSnackbar(message: String) {
        _snackbar.value = message
    }

    fun consumeSnackbar() {
        _snackbar.value = null
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))

private fun epochMsToIstanbulDate(ms: Long): LocalDate =
    Instant.ofEpochMilli(ms).atZone(ISTANBUL).toLocalDate()

private fun epochMsToIstanbulTime(ms: Long): LocalTime =
    Instant.ofEpochMilli(ms).atZone(ISTANBUL).toLocalTime()

private fun formatTime(ms: Long): String {
    val t = epochMsToIstanbulTime(ms)
    return "%02d:%02d".format(t.hour, t.minute)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenImportHub: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingDate by remember { mutableStateOf<String?>(null) } // "tyt" | "ayt"
    var editingTime by remember { mutableStateOf<String?>(null) } // "tyt" | "ayt"
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pendingImportUri = it } }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setBackupDir(it.toString())
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ayarlar") }) },
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sınav Tarihleri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingDate = "tyt" }) {
                            Text("TYT: ${epochMsToIstanbulDate(settings.tytExamAt).format(dateFormat)}")
                        }
                        OutlinedButton(onClick = { editingTime = "tyt" }) {
                            Text(formatTime(settings.tytExamAt))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingDate = "ayt" }) {
                            Text("AYT: ${epochMsToIstanbulDate(settings.aytExamAt).format(dateFormat)}")
                        }
                        OutlinedButton(onClick = { editingTime = "ayt" }) {
                            Text(formatTime(settings.aytExamAt))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tarihler ÖSYM tarafından açıklandı", style = MaterialTheme.typography.bodyMedium)
                            if (!settings.datesConfirmed) {
                                Text(
                                    "Kapalıyken sayaçta \"tahmini tarih\" uyarısı görünür",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(checked = settings.datesConfirmed, onCheckedChange = viewModel::setDatesConfirmed)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    listOf(
                        ThemeMode.SYSTEM to "Sistem",
                        ThemeMode.LIGHT to "Açık",
                        ThemeMode.DARK to "Koyu",
                    ).forEach { (mode, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            AiProfilesCard(onSnackbar = { message ->
                viewModel.showSnackbar(message)
            })

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Yedekleme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tüm veriler tek bir JSON dosyasına aktarılır. Geri yükleme mevcut " +
                            "verilerin tamamını yedekle DEĞİŞTİRİR (öncesinde otomatik güvenlik " +
                            "kopyası alınır).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch(viewModel.suggestedBackupName()) }) {
                            Text("Dışa Aktar")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Text("Geri Yükle")
                        }
                        OutlinedButton(onClick = onOpenImportHub) {
                            Text("İçe Aktar (CSV / AI)")
                        }
                    }
                    Text(
                        "İçe Aktar mevcut verilerin ÜZERİNE ekler (CSV veya AI ile okuma); " +
                            "Geri Yükle ise her şeyi seçilen JSON yedekle değiştirir. " +
                            "İpucu: otomatik yedek klasörünü Google Drive ile eşitlenen ve " +
                            "abinle paylaşılan bir klasöre yönlendirirsen haftalık yedekler " +
                            "kendiliğinden ona da ulaşır — uygulamada hesap ya da takip yok.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        buildString {
                            append(
                                if (settings.backupDirUri != null) {
                                    "Otomatik yedekleme: açık (haftalık, son 8 yedek tutulur)."
                                } else {
                                    "Otomatik yedekleme: kapalı."
                                },
                            )
                            settings.lastBackupAt?.let {
                                append(" Son yedek: ${epochMsToIstanbulDate(it).format(dateFormat)}.")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { treeLauncher.launch(null) }) {
                            Text(if (settings.backupDirUri != null) "Klasörü Değiştir" else "Otomatik Yedek Klasörü Seç")
                        }
                        if (settings.backupDirUri != null) {
                            TextButton(onClick = viewModel::clearBackupDir) { Text("Kapat") }
                        }
                    }
                }
            }

            Text(
                "YKS Takip (${settings.yksYearLabel}) · ${BackupManager.APP_VERSION} · veriler yalnızca bu cihazda",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingDate?.let { which ->
        val currentMs = if (which == "tyt") settings.tytExamAt else settings.aytExamAt
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = epochMsToIstanbulDate(currentMs)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { editingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "tyt") viewModel.setTytDate(date) else viewModel.setAytDate(date)
                    }
                    editingDate = null
                }) { Text("Tamam") }
            },
            dismissButton = {
                TextButton(onClick = { editingDate = null }) { Text("Vazgeç") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    editingTime?.let { which ->
        val currentMs = if (which == "tyt") settings.tytExamAt else settings.aytExamAt
        val current = epochMsToIstanbulTime(currentMs)
        val timeState = androidx.compose.material3.rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = { Text(if (which == "tyt") "TYT başlangıç saati" else "AYT başlangıç saati") },
            text = { androidx.compose.material3.TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    if (which == "tyt") {
                        viewModel.setTytTime(timeState.hour, timeState.minute)
                    } else {
                        viewModel.setAytTime(timeState.hour, timeState.minute)
                    }
                    editingTime = null
                }) { Text("Tamam") }
            },
            dismissButton = {
                TextButton(onClick = { editingTime = null }) { Text("Vazgeç") }
            },
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Yedeği geri yükle") },
            text = {
                Text(
                    "Mevcut tüm veriler silinip seçilen yedekle değiştirilecek. " +
                        "Devam etmeden önce otomatik bir güvenlik kopyası alınır.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importFrom(uri)
                    pendingImportUri = null
                }) { Text("Geri Yükle") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Vazgeç") }
            },
        )
    }
}
