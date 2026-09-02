package com.yks2027.tracker.feature.notes

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mikepenz.markdown.m3.Markdown
import com.yks2027.tracker.core.database.NoteDao
import com.yks2027.tracker.core.database.NoteEntity
import com.yks2027.tracker.core.time.ISTANBUL
import com.yks2027.tracker.core.time.IstanbulClock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * v1.2 — Notlar: markdown notes in Room (backup v4 carries them). Editor is a plain
 * text field; preview renders via the maintained mikepenz multiplatform-markdown-
 * renderer (verified on Maven Central 2026-08-30, v0.45.0). AI Koç's "Nota kaydet"
 * lands assistant messages here.
 */

data class NoteDraft(
    /** 0 = new note. */
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val createdAt: Long = 0,
)

class NotesViewModel constructor(
    private val noteDao: NoteDao,
    private val clock: IstanbulClock,
) : ViewModel() {

    val notes = noteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow<NoteDraft?>(null)
    val draft = _draft.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    private var lastDeleted: NoteEntity? = null

    fun newNote() {
        _draft.value = NoteDraft()
    }

    fun edit(note: NoteEntity) {
        _draft.value = NoteDraft(id = note.id, title = note.title, body = note.body, createdAt = note.createdAt)
    }

    fun updateDraft(block: NoteDraft.() -> NoteDraft) {
        _draft.value = _draft.value?.block()
    }

    fun closeDraft() {
        _draft.value = null
    }

    fun saveDraft() {
        val d = _draft.value ?: return
        if (d.title.isBlank() && d.body.isBlank()) {
            _draft.value = null
            return
        }
        viewModelScope.launch {
            val now = clock.now().toEpochMilli()
            noteDao.upsert(
                NoteEntity(
                    id = d.id,
                    title = d.title.trim().ifBlank { d.body.lineSequence().first().take(48) },
                    body = d.body,
                    createdAt = if (d.id == 0L) now else d.createdAt,
                    updatedAt = now,
                ),
            )
            _draft.value = null
            _snackbar.value = "Not kaydedildi"
        }
    }

    fun deleteDraftNote() {
        val d = _draft.value ?: return
        _draft.value = null
        if (d.id == 0L) return
        viewModelScope.launch {
            lastDeleted = noteDao.byId(d.id)
            noteDao.deleteById(d.id)
            _snackbar.value = "Not silindi"
        }
    }

    fun undoDelete() {
        val note = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { noteDao.upsert(note) }
    }

    fun consumeSnackbar() {
        _snackbar.value = null
    }
}

private val noteDate = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("tr"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel = koinViewModel()) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = if (msg == "Not silindi") "Geri Al" else null)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        viewModel.consumeSnackbar()
    }

    val currentDraft = draft
    if (currentDraft != null) {
        NoteEditor(
            draft = currentDraft,
            onUpdate = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteDraftNote,
            onBack = viewModel::saveDraft, // back = save-and-close (never lose text)
            snackbarHostState = snackbarHostState,
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notlar") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::newNote) {
                Icon(Icons.Outlined.Add, contentDescription = "Yeni not")
            }
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(
                    "Henüz not yok. Sağ alttan yeni not ekleyebilir, AI Koç yanıtlarını " +
                        "\"Nota kaydet\" ile buraya alabilirsin. Markdown desteklenir " +
                        "(# başlık, **kalın**, - liste).",
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
            items(notes, key = { it.id }) { note ->
                Card(onClick = { viewModel.edit(note) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            note.title.ifBlank { "(Başlıksız)" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            note.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(120) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                        Text(
                            Instant.ofEpochMilli(note.updatedAt).atZone(ISTANBUL).format(noteDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(
    draft: NoteDraft,
    onUpdate: ((NoteDraft.() -> NoteDraft)) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var preview by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "Yeni Not" else "Notu Düzenle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    FilterChip(
                        selected = preview,
                        onClick = { preview = !preview },
                        label = { Text("Önizleme") },
                    )
                    if (draft.id != 0L) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Sil")
                        }
                    }
                    TextButton(onClick = onSave) { Text("Kaydet") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> onUpdate { copy(title = v) } },
                label = { Text("Başlık") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (preview) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Markdown(content = draft.body.ifBlank { "_Önizlenecek metin yok_" })
                }
            } else {
                OutlinedTextField(
                    value = draft.body,
                    onValueChange = { v -> onUpdate { copy(body = v) } },
                    label = { Text("Not (Markdown desteklenir)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            Row {
                Text(
                    "Markdown: # başlık · **kalın** · *eğik* · - liste · 1. sıralı liste",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
