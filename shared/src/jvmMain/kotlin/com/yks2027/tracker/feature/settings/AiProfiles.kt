package com.yks2027.tracker.feature.settings

import com.yks2027.tracker.core.platform.SecretStore
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.ai.AiClient
import com.yks2027.tracker.core.ai.AiException
import com.yks2027.tracker.core.ai.AiModelInfo
import com.yks2027.tracker.core.ai.AiProfilesRepository
import com.yks2027.tracker.core.ai.AiProtocol
import com.yks2027.tracker.core.ai.AiTemplate
import com.yks2027.tracker.core.database.AiProfileEntity
import com.yks2027.tracker.core.datastore.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * v1.2 — AI profile manager (replaces the single-slot provider card). Fixes the
 * misrouted-key bug by binding key + protocol + base URL + model together per profile.
 */

data class ProfileEditorState(
    /** 0 = creating a new profile. */
    val id: Long = 0,
    val name: String = "",
    val protocol: AiProtocol = AiProtocol.OPENAI_COMPAT,
    val baseUrl: String = "",
    val model: String = "",
    val keyInput: String = "",
    val hasStoredKey: Boolean = false,
    val keyHint: String? = null,
    val busy: Boolean = false,
    /** Result line of Bağlantıyı Sına / Modelleri Getir — success or exact failure. */
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val fetchedModels: List<AiModelInfo> = emptyList(),
)

class AiProfilesViewModel constructor(
    private val profilesRepository: AiProfilesRepository,
    private val secrets: SecretStore,
    private val aiClient: AiClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val profiles = profilesRepository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeId = settingsRepository.settings.map { it.activeAiProfileId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val keysPresent = secrets.profileIdsWithKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val shareStats = settingsRepository.settings.map { it.aiShareStats }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _editor = MutableStateFlow<ProfileEditorState?>(null)
    val editor = _editor.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    private var probeJob: Job? = null

    fun startFromTemplate(template: AiTemplate) {
        _editor.value = ProfileEditorState(
            name = if (template.baseUrl.isEmpty()) "" else template.name,
            protocol = template.protocol,
            baseUrl = template.baseUrl,
            model = template.model,
            keyHint = template.keyHint,
        )
    }

    fun startEdit(profile: AiProfileEntity) {
        _editor.value = ProfileEditorState(
            id = profile.id,
            name = profile.name,
            protocol = profilesRepository.protocolOf(profile),
            baseUrl = profile.baseUrl,
            model = profile.model,
            hasStoredKey = keysPresent.value.contains(profile.id),
        )
    }

    fun dismissEditor() {
        probeJob?.cancel()
        _editor.value = null
    }

    fun updateEditor(block: ProfileEditorState.() -> ProfileEditorState) {
        _editor.value = _editor.value?.block()
    }

    fun saveEditor() {
        val e = _editor.value ?: return
        if (e.name.isBlank()) return
        viewModelScope.launch {
            val id = profilesRepository.save(
                AiProfileEntity(
                    id = e.id,
                    name = e.name.trim(),
                    protocol = e.protocol.name,
                    baseUrl = e.baseUrl.trim().trimEnd('/'),
                    model = e.model.trim(),
                    createdAt = 0, // repository stamps on create, preserves on edit? (see save)
                ),
            )
            if (e.keyInput.isNotBlank()) secrets.setKey(id, e.keyInput)
            _editor.value = null
            _snackbar.value = "Profil kaydedildi"
        }
    }

    fun deleteEditorProfile() {
        val e = _editor.value ?: return
        if (e.id == 0L) {
            _editor.value = null
            return
        }
        viewModelScope.launch {
            profilesRepository.delete(e.id)
            _editor.value = null
            _snackbar.value = "Profil ve anahtarı silindi"
        }
    }

    fun clearStoredKey() {
        val e = _editor.value ?: return
        if (e.id == 0L) return
        viewModelScope.launch {
            secrets.clearKey(e.id)
            updateEditor { copy(hasStoredKey = false, keyInput = "") }
            _snackbar.value = "Anahtar silindi"
        }
    }

    fun setActive(id: Long) = viewModelScope.launch { profilesRepository.setActive(id) }
    fun setShareStats(v: Boolean) = viewModelScope.launch { settingsRepository.setAiShareStats(v) }
    fun consumeSnackbar() { _snackbar.value = null }

    /** Bağlantıyı Sına — models-list probe with the editor's CURRENT (unsaved) values. */
    fun testConnection() = probe(fetchList = false)

    /** Modelleri Getir — same endpoint, keeps the list for the picker. */
    fun fetchModels() = probe(fetchList = true)

    private fun probe(fetchList: Boolean) {
        val e = _editor.value ?: return
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            updateEditor { copy(busy = true, statusMessage = null, statusIsError = false) }
            val temp = AiProfileEntity(
                id = e.id, name = e.name, protocol = e.protocol.name,
                baseUrl = e.baseUrl.trim().trimEnd('/'), model = e.model, createdAt = 0,
            )
            val result = runCatching {
                aiClient.listModels(temp, keyOverride = e.keyInput.takeIf { it.isNotBlank() })
            }
            result.onSuccess { models ->
                updateEditor {
                    copy(
                        busy = false,
                        statusMessage = "Bağlantı başarılı — ${models.size} model listelendi.",
                        statusIsError = false,
                        fetchedModels = if (fetchList) models else fetchedModels,
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = (error as? AiException)?.message
                    ?: "Beklenmeyen hata: ${error.message?.take(160)}"
                updateEditor { copy(busy = false, statusMessage = message, statusIsError = true) }
            }
        }
    }
}

@Composable
fun AiProfilesCard(viewModel: AiProfilesViewModel = koinViewModel(), onSnackbar: (String) -> Unit) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val keysPresent by viewModel.keysPresent.collectAsStateWithLifecycle()
    val shareStats by viewModel.shareStats.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val snackbar by viewModel.snackbar.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(snackbar) {
        snackbar?.let {
            onSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    var templateMenuOpen by remember { mutableStateOf(false) }
    val effectiveActiveId = activeId ?: profiles.firstOrNull()?.id

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI Koç — Profiller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Her profil kendi sağlayıcısını, adresini, modelini ve anahtarını taşır; " +
                    "anahtarlar cihazda şifreli saklanır ve yedeklere GİRMEZ. Profil " +
                    "eklenmeden uygulama hiçbir ağ isteği yapmaz. Sağlayıcı panelinden " +
                    "harcama limiti koymak iyi fikir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (profiles.isEmpty()) {
                Text(
                    "Henüz profil yok — şablondan ekleyerek başla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            profiles.forEach { profile ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = profile.id == effectiveActiveId,
                        onClick = { viewModel.setActive(profile.id) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            buildString {
                                append(if (profile.protocol == AiProtocol.ANTHROPIC.name) "Anthropic" else "OpenAI uyumlu")
                                append(" · ").append(profile.model.ifBlank { "model seçilmedi" })
                                append(if (keysPresent.contains(profile.id)) " · anahtar ✓" else " · anahtar yok")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.startEdit(profile) }) { Text("Düzenle") }
                }
            }

            Box {
                OutlinedButton(onClick = { templateMenuOpen = true }) { Text("+ Profil Ekle") }
                DropdownMenu(expanded = templateMenuOpen, onDismissRequest = { templateMenuOpen = false }) {
                    AiTemplate.ALL.forEach { template ->
                        DropdownMenuItem(
                            text = { Text(template.name) },
                            onClick = {
                                viewModel.startFromTemplate(template)
                                templateMenuOpen = false
                            },
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("İstatistikleri koçla paylaş", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Açıkken son netler, plan durumu, çalışma süresi ve zayıf konular mesajlara eklenir.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(checked = shareStats, onCheckedChange = viewModel::setShareStats)
            }
        }
    }

    editor?.let { e ->
        ProfileEditorDialog(
            state = e,
            onUpdate = viewModel::updateEditor,
            onSave = viewModel::saveEditor,
            onDelete = viewModel::deleteEditorProfile,
            onClearKey = viewModel::clearStoredKey,
            onTest = viewModel::testConnection,
            onFetchModels = viewModel::fetchModels,
            onDismiss = viewModel::dismissEditor,
        )
    }
}

@Composable
private fun ProfileEditorDialog(
    state: ProfileEditorState,
    onUpdate: ((ProfileEditorState.() -> ProfileEditorState)) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClearKey: () -> Unit,
    onTest: () -> Unit,
    onFetchModels: () -> Unit,
    onDismiss: () -> Unit,
) {
    var modelPickerOpen by rememberSaveable { mutableStateOf(false) }
    var modelFilter by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.id == 0L) "Yeni AI profili" else "Profili düzenle") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { v -> onUpdate { copy(name = v) } },
                    label = { Text("Profil adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProtocol.entries.forEach { protocol ->
                        FilterChip(
                            selected = state.protocol == protocol,
                            onClick = { onUpdate { copy(protocol = protocol, fetchedModels = emptyList()) } },
                            label = { Text(protocol.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = { v -> onUpdate { copy(baseUrl = v) } },
                    label = { Text("Taban URL") },
                    supportingText = {
                        if (state.protocol == AiProtocol.ANTHROPIC) {
                            Text("Varsayılan: https://api.anthropic.com (vekil sunucu için değiştirilebilir)")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.model,
                    onValueChange = { v -> onUpdate { copy(model = v) } },
                    label = { Text("Model (elle yazılabilir)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        onFetchModels()
                        modelPickerOpen = true
                    }, enabled = !state.busy) { Text("Modelleri Getir") }
                    OutlinedButton(onClick = onTest, enabled = !state.busy) { Text("Bağlantıyı Sına") }
                    if (state.busy) CircularProgressIndicator(Modifier.padding(start = 4.dp), strokeWidth = 2.dp)
                }
                state.statusMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                if (modelPickerOpen && state.fetchedModels.isNotEmpty()) {
                    OutlinedTextField(
                        value = modelFilter,
                        onValueChange = { modelFilter = it },
                        label = { Text("Model ara") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val filtered = state.fetchedModels.filter {
                        modelFilter.isBlank() ||
                            it.id.contains(modelFilter, true) ||
                            it.displayName?.contains(modelFilter, true) == true
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(filtered, key = { it.id }) { model ->
                            TextButton(onClick = {
                                onUpdate { copy(model = model.id) }
                                modelPickerOpen = false
                            }) {
                                Text(
                                    model.displayName?.takeIf { it.isNotBlank() && it != model.id }
                                        ?.let { "${model.id} — $it" } ?: model.id,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = state.keyInput,
                    onValueChange = { v -> onUpdate { copy(keyInput = v) } },
                    label = {
                        Text(
                            when {
                                state.hasStoredKey -> "API anahtarı (kayıtlı ✓ — değiştirmek için yaz)"
                                else -> "API anahtarı (opsiyonel — Ollama gibi sunucular istemez)"
                            },
                        )
                    },
                    supportingText = state.keyHint?.let { hint -> { Text("Anahtar: $hint") } },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.hasStoredKey) {
                    TextButton(onClick = onClearKey) { Text("Kayıtlı anahtarı sil") }
                }
                if (state.id != 0L) {
                    TextButton(onClick = onDelete) {
                        Text("Profili Sil", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.name.isNotBlank()) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}
