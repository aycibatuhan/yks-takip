package com.yks2027.tracker.feature.aikoc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.yks2027.tracker.core.ai.AiChatMessage
import com.yks2027.tracker.core.ai.AiClient
import com.yks2027.tracker.core.ai.AiException
import com.yks2027.tracker.core.database.ChatDao
import com.yks2027.tracker.core.database.ChatMessageEntity
import com.yks2027.tracker.core.database.ChatThreadEntity
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.time.ISTANBUL
import com.yks2027.tracker.core.time.IstanbulClock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiHeaderInfo(
    val profileName: String = "",
    val model: String = "",
    val shareStats: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiKocViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val aiClient: AiClient,
    private val settingsRepository: SettingsRepository,
    private val profilesRepository: com.yks2027.tracker.core.ai.AiProfilesRepository,
    private val noteDao: com.yks2027.tracker.core.database.NoteDao,
    private val clock: IstanbulClock,
) : ViewModel() {

    private val currentThreadId = MutableStateFlow<Long?>(null)

    val threads = chatDao.observeThreads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messages = currentThreadId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else chatDao.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- v1.3 sessions pane: search + folder filter + ordered thread overviews ---

    val searchQuery = MutableStateFlow("")
    val folderFilter = MutableStateFlow<SessionListLogic.FolderFilter>(SessionListLogic.FolderFilter.All)

    val folders = chatDao.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val contentMatchIds = searchQuery
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) flowOf(emptyList()) else chatDao.observeContentMatchThreadIds(trimmed)
        }

    val sessionRows = kotlinx.coroutines.flow.combine(
        chatDao.observeThreadOverviews(),
        searchQuery,
        folderFilter,
        contentMatchIds,
    ) { overviews, query, filter, matchIds ->
        SessionListLogic.filter(overviews, filter, query, matchIds.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Top-bar title source: the selected thread's overview (null = fresh chat). */
    val currentThread = kotlinx.coroutines.flow.combine(
        chatDao.observeThreadOverviews(),
        currentThreadId,
    ) { overviews, id -> overviews.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setFolderFilter(filter: SessionListLogic.FolderFilter) {
        folderFilter.value = filter
    }

    fun renameThread(threadId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { chatDao.renameThread(threadId, trimmed.take(80)) }
    }

    fun togglePinned(thread: com.yks2027.tracker.core.database.ThreadOverview) {
        viewModelScope.launch { chatDao.setThreadPinned(thread.id, !thread.pinned) }
    }

    fun moveThreadToFolder(threadId: Long, folderId: Long?) {
        viewModelScope.launch { chatDao.setThreadFolder(threadId, folderId) }
    }

    fun createFolder(name: String, thenAssignThreadId: Long? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = chatDao.insertFolder(
                com.yks2027.tracker.core.database.ChatFolderEntity(
                    name = trimmed.take(40), createdAt = clock.now().toEpochMilli(),
                ),
            )
            thenAssignThreadId?.let { chatDao.setThreadFolder(it, id) }
        }
    }

    fun renameFolder(folderId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { chatDao.renameFolder(folderId, trimmed.take(40)) }
    }

    /** FK SET_NULL: threads survive and drop to "Klasörsüz" — never deleted with a folder. */
    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            chatDao.deleteFolder(folderId)
            if ((folderFilter.value as? SessionListLogic.FolderFilter.Folder)?.id == folderId) {
                folderFilter.value = SessionListLogic.FolderFilter.All
            }
            _noteSaved.value = "Klasör silindi — sohbetler Klasörsüz'e taşındı"
        }
    }

    // Delete-with-undo: the thread AND its messages are captured before the cascade.
    private var lastDeletedThread: com.yks2027.tracker.core.database.ChatThreadEntity? = null
    private var lastDeletedMessages: List<ChatMessageEntity> = emptyList()

    private val _threadDeleted = MutableStateFlow<String?>(null)
    val threadDeleted = _threadDeleted.asStateFlow()

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            lastDeletedThread = chatDao.threadById(threadId)
            lastDeletedMessages = chatDao.messagesForThreadOnce(threadId)
            chatDao.deleteThread(threadId)
            if (currentThreadId.value == threadId) currentThreadId.value = null
            _threadDeleted.value = "Sohbet silindi"
        }
    }

    fun undoDeleteThread() {
        val thread = lastDeletedThread ?: return
        val messages = lastDeletedMessages
        lastDeletedThread = null
        lastDeletedMessages = emptyList()
        viewModelScope.launch {
            chatDao.insertThread(thread) // explicit id — pin/folder/ordering all survive
            messages.forEach { chatDao.insertMessage(it.copy(id = 0)) }
        }
    }

    fun consumeThreadDeleted() {
        _threadDeleted.value = null
    }

    /** v1.2 — header shows the ACTIVE PROFILE (name + model), not a global setting. */
    val header = kotlinx.coroutines.flow.combine(
        profilesRepository.activeProfile,
        settingsRepository.settings,
    ) { profile, settings ->
        AiHeaderInfo(
            profileName = profile?.name.orEmpty(),
            model = profile?.model.orEmpty(),
            shareStats = settings.aiShareStats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiHeaderInfo())

    /** Quick profile switcher data. */
    val profiles = profilesRepository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun switchProfile(id: Long) {
        viewModelScope.launch { profilesRepository.setActive(id) }
    }

    /** v1.2 — "Nota kaydet": lands an assistant message in Notlar as markdown. */
    fun saveMessageAsNote(message: ChatMessageEntity) {
        viewModelScope.launch {
            val now = clock.now().toEpochMilli()
            val threadTitle = threads.value.firstOrNull { it.id == message.threadId }?.title
            noteDao.upsert(
                com.yks2027.tracker.core.database.NoteEntity(
                    title = (threadTitle ?: message.content.lineSequence().first()).take(48),
                    body = message.content,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _noteSaved.value = "Not kaydedildi — Notlar sekmesinde"
        }
    }

    private val _noteSaved = MutableStateFlow<String?>(null)
    val noteSaved = _noteSaved.asStateFlow()

    fun consumeNoteSaved() {
        _noteSaved.value = null
    }

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText = _streamingText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private var streamJob: Job? = null

    fun selectThread(id: Long) {
        if (_busy.value) return
        currentThreadId.value = id
    }

    fun newChat() {
        if (_busy.value) return
        currentThreadId.value = null
        _errorMessage.value = null
    }

    fun stopStreaming() {
        streamJob?.cancel()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _busy.value) return
        streamJob = viewModelScope.launch {
            _busy.value = true
            _errorMessage.value = null
            val now = clock.now().toEpochMilli()
            val assembled = StringBuilder()
            val model = profilesRepository.activeProfileOnce()?.model.orEmpty()
            try {
                val threadId = currentThreadId.value ?: chatDao.insertThread(
                    ChatThreadEntity(title = trimmed.take(48), createdAt = now),
                ).also { currentThreadId.value = it }
                chatDao.insertMessage(
                    ChatMessageEntity(
                        threadId = threadId, role = AiChatMessage.ROLE_USER,
                        content = trimmed, model = null, createdAt = now,
                    ),
                )
                val history = chatDao.messagesForThreadOnce(threadId)
                    .takeLast(HISTORY_WINDOW)
                    .map { AiChatMessage(it.role, it.content) }
                _streamingText.value = ""
                aiClient.streamReply(history).collect { delta ->
                    assembled.append(delta)
                    _streamingText.value = assembled.toString()
                }
                persistAssistant(assembled.toString(), model)
            } catch (e: CancellationException) {
                // User pressed stop — keep whatever arrived.
                persistAssistant(assembled.toString(), model)
            } catch (e: AiException) {
                persistAssistant(assembled.toString(), model)
                _errorMessage.value = e.message
            } catch (e: Exception) {
                persistAssistant(assembled.toString(), model)
                _errorMessage.value = "Beklenmeyen hata: ${e.message?.take(200)}"
            } finally {
                _streamingText.value = null
                _busy.value = false
            }
        }
    }

    private suspend fun persistAssistant(content: String, model: String) {
        val threadId = currentThreadId.value ?: return
        if (content.isBlank()) return
        chatDao.insertMessage(
            ChatMessageEntity(
                threadId = threadId, role = AiChatMessage.ROLE_ASSISTANT,
                content = content, model = model, createdAt = clock.now().toEpochMilli(),
            ),
        )
    }

    companion object {
        private const val HISTORY_WINDOW = 24
    }
}

private val threadDate = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("tr"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiKocScreen(viewModel: AiKocViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val noteSaved by viewModel.noteSaved.collectAsStateWithLifecycle()
    val threadDeleted by viewModel.threadDeleted.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    // v1.3 sessions pane state
    val sessionRows by viewModel.sessionRows.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val folderFilter by viewModel.folderFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentThread by viewModel.currentThread.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var sessionsSheetOpen by remember { mutableStateOf(false) }
    var renameCurrentOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val expanded = com.yks2027.tracker.core.ui.isExpandedWidth()
    val nowMs = remember(sessionRows) { System.currentTimeMillis() }

    LaunchedEffect(messages.size, streamingText?.length) {
        val count = messages.size + (if (streamingText != null) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(noteSaved) {
        val msg = noteSaved ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeNoteSaved()
    }

    LaunchedEffect(threadDeleted) {
        val msg = threadDeleted ?: return@LaunchedEffect
        // consume() must come AFTER showSnackbar: nulling the key first restarts this
        // effect and cancels the suspended snackbar before it ever renders.
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Geri Al")
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undoDeleteThread()
        viewModel.consumeThreadDeleted()
    }

    val paneCallbacks = SessionsPaneCallbacks(
        onSelectThread = { id ->
            viewModel.selectThread(id)
            sessionsSheetOpen = false
        },
        onNewChat = {
            viewModel.newChat()
            sessionsSheetOpen = false
        },
        onQueryChange = viewModel::setSearchQuery,
        onFilterChange = viewModel::setFolderFilter,
        onRenameThread = viewModel::renameThread,
        onTogglePin = viewModel::togglePinned,
        onMoveToFolder = viewModel::moveThreadToFolder,
        onCreateFolder = viewModel::createFolder,
        onRenameFolder = viewModel::renameFolder,
        onDeleteFolder = viewModel::deleteFolder,
        onDeleteThread = viewModel::deleteThread,
    )

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // v1.3 — the thread title IS the top bar title; tap to rename.
                    Column(
                        Modifier.then(
                            if (currentThread != null) {
                                Modifier.clickable { renameCurrentOpen = true }
                            } else {
                                Modifier
                            },
                        ),
                    ) {
                        Text(currentThread?.title ?: "Yeni sohbet", maxLines = 1)
                        Text(
                            listOfNotNull(
                                header.profileName.takeIf { it.isNotBlank() },
                                header.model.takeIf { it.isNotBlank() },
                                "veri paylaşımı kapalı".takeIf { !header.shareStats },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { profileMenuOpen = true }) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Profil değiştir")
                        }
                        DropdownMenu(expanded = profileMenuOpen, onDismissRequest = { profileMenuOpen = false }) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (profile.name == header.profileName) "✓ " else "") +
                                                "${profile.name} · ${profile.model.ifBlank { "model yok" }}",
                                        )
                                    },
                                    onClick = {
                                        viewModel.switchProfile(profile.id)
                                        profileMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    if (!expanded) {
                        IconButton(onClick = { sessionsSheetOpen = true }) {
                            Icon(Icons.Outlined.History, contentDescription = "Sohbetler")
                        }
                    }
                    IconButton(onClick = viewModel::newChat) {
                        Icon(Icons.Outlined.Add, contentDescription = "Yeni sohbet")
                    }
                },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (expanded) {
                // v1.3 — permanent sessions pane at Expanded width.
                SessionsPane(
                    rows = sessionRows,
                    folders = folders,
                    filter = folderFilter,
                    query = searchQuery,
                    activeThreadId = currentThread?.id,
                    nowMs = nowMs,
                    callbacks = paneCallbacks,
                    modifier = Modifier.width(320.dp),
                )
                androidx.compose.material3.VerticalDivider()
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (messages.isEmpty() && streamingText == null) {
                        item {
                            Text(
                                "Koçuna sor: \"Son denemelerime göre hangi derse yüklenmeliyim?\", " +
                                    "\"Bu hafta için plan tasla\", \"Türev nerede işime yarayacak?\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            content = message.content,
                            isUser = message.role == AiChatMessage.ROLE_USER,
                            onSaveNote = if (message.role == AiChatMessage.ROLE_ASSISTANT) {
                                { viewModel.saveMessageAsNote(message) }
                            } else {
                                null
                            },
                        )
                    }
                    streamingText?.let { partial ->
                        item(key = "streaming") {
                            MessageBubble(content = partial.ifEmpty { "…" }, isUser = false)
                        }
                    }
                }

                errorMessage?.let { error ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                error,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            IconButton(onClick = viewModel::dismissError) { Text("✕") }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Koçuna yaz…") },
                        maxLines = 4,
                    )
                    if (busy) {
                        IconButton(onClick = viewModel::stopStreaming) {
                            Icon(Icons.Outlined.Stop, contentDescription = "Durdur", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.send(input)
                                input = ""
                            },
                            enabled = input.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gönder")
                        }
                    }
                }
                Text(
                    "AI hata yapabilir — öğretmen gibi düşün, cevap anahtarı gibi değil.",
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // v1.3 — Compact width: the sessions pane lives in a bottom sheet.
    if (!expanded && sessionsSheetOpen) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sessionsSheetOpen = false }) {
            SessionsPane(
                rows = sessionRows,
                folders = folders,
                filter = folderFilter,
                query = searchQuery,
                activeThreadId = currentThread?.id,
                nowMs = nowMs,
                callbacks = paneCallbacks,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (renameCurrentOpen) {
        currentThread?.let { thread ->
            var value by remember(thread.id) { mutableStateOf(thread.title.orEmpty()) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { renameCurrentOpen = false },
                title = { Text("Sohbeti yeniden adlandır") },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Ad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.renameThread(thread.id, value)
                            renameCurrentOpen = false
                        },
                        enabled = value.isNotBlank(),
                    ) { Text("Kaydet") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { renameCurrentOpen = false }) { Text("Vazgeç") }
                },
            )
        }
    }
}

@Composable
private fun MessageBubble(content: String, isUser: Boolean, onSaveNote: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            SelectionContainer {
                Text(
                    content,
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
        // v1.2 — "Nota kaydet" on assistant messages.
        if (onSaveNote != null) {
            IconButton(onClick = onSaveNote) {
                Icon(
                    Icons.Outlined.BookmarkAdd,
                    contentDescription = "Nota kaydet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
