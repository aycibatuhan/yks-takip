package com.yks2027.tracker.feature.aikoc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yks2027.tracker.core.database.ChatFolderEntity
import com.yks2027.tracker.core.database.ThreadOverview
import java.time.Instant

/**
 * v1.3 — the sessions pane: search, folder chips, ordered thread list with per-thread
 * actions. One composable serves both hosts: permanent left pane at Expanded width,
 * bottom sheet behind the history icon at Compact.
 */

data class SessionsPaneCallbacks(
    val onSelectThread: (Long) -> Unit,
    val onNewChat: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (SessionListLogic.FolderFilter) -> Unit,
    val onRenameThread: (Long, String) -> Unit,
    val onTogglePin: (ThreadOverview) -> Unit,
    val onMoveToFolder: (Long, Long?) -> Unit,
    val onCreateFolder: (String, Long?) -> Unit,
    val onRenameFolder: (Long, String) -> Unit,
    val onDeleteFolder: (Long) -> Unit,
    val onDeleteThread: (Long) -> Unit,
)

@Composable
fun SessionsPane(
    rows: List<ThreadOverview>,
    folders: List<ChatFolderEntity>,
    filter: SessionListLogic.FolderFilter,
    query: String,
    activeThreadId: Long?,
    nowMs: Long,
    callbacks: SessionsPaneCallbacks,
    modifier: Modifier = Modifier,
) {
    var renameThreadTarget by remember { mutableStateOf<ThreadOverview?>(null) }
    var deleteThreadTarget by remember { mutableStateOf<ThreadOverview?>(null) }
    var moveThreadTarget by remember { mutableStateOf<ThreadOverview?>(null) }
    var createFolderForThread by remember { mutableStateOf<Long?>(null) }
    var createFolderOpen by remember { mutableStateOf(false) }
    // Folder long-press flow: action menu → rename-entry OR delete-confirm.
    var folderActionTarget by remember { mutableStateOf<ChatFolderEntity?>(null) }
    var renameFolderNameTarget by remember { mutableStateOf<ChatFolderEntity?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<ChatFolderEntity?>(null) }

    val folderNames = folders.associate { it.id to it.name }

    Column(modifier.fillMaxHeight().padding(horizontal = 10.dp)) {
        Button(
            onClick = callbacks.onNewChat,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text(" Yeni sohbet")
        }

        OutlinedTextField(
            value = query,
            onValueChange = callbacks.onQueryChange,
            label = { Text("Sohbetlerde ara") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            item {
                FolderChip(
                    label = "Tümü",
                    selected = filter == SessionListLogic.FolderFilter.All,
                    onClick = { callbacks.onFilterChange(SessionListLogic.FolderFilter.All) },
                )
            }
            item {
                FolderChip(
                    label = "Klasörsüz",
                    selected = filter == SessionListLogic.FolderFilter.Unfiled,
                    onClick = { callbacks.onFilterChange(SessionListLogic.FolderFilter.Unfiled) },
                )
            }
            items(folders, key = { it.id }) { folder ->
                FolderChip(
                    label = folder.name,
                    selected = (filter as? SessionListLogic.FolderFilter.Folder)?.id == folder.id,
                    onClick = { callbacks.onFilterChange(SessionListLogic.FolderFilter.Folder(folder.id)) },
                    onLongClick = { folderActionTarget = folder },
                )
            }
            item {
                FolderChip(label = "+", selected = false, onClick = {
                    createFolderForThread = null
                    createFolderOpen = true
                })
            }
        }

        if (rows.isEmpty()) {
            Text(
                if (query.isNotBlank()) {
                    "Eşleşen sohbet yok."
                } else if (folders.isEmpty() && filter == SessionListLogic.FolderFilter.All) {
                    "Henüz sohbet yok. İpucu: sohbetleri \"+\" ile açacağın ders " +
                        "klasörlerine (Matematik, Fizik, Motivasyon…) ayırabilirsin."
                } else {
                    "Bu görünümde sohbet yok."
                },
                Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        ) {
            items(rows, key = { it.id }) { row ->
                ThreadRow(
                    row = row,
                    active = row.id == activeThreadId,
                    folderBadge = row.folderId?.let(folderNames::get)
                        ?.takeIf { (filter as? SessionListLogic.FolderFilter.Folder)?.id != row.folderId },
                    nowMs = nowMs,
                    onClick = { callbacks.onSelectThread(row.id) },
                    onRename = { renameThreadTarget = row },
                    onTogglePin = { callbacks.onTogglePin(row) },
                    onMove = { moveThreadTarget = row },
                    onDelete = { deleteThreadTarget = row },
                )
            }
        }
    }

    // --- dialogs ---

    renameThreadTarget?.let { target ->
        NameDialog(
            title = "Sohbeti yeniden adlandır",
            initial = target.title.orEmpty(),
            confirmLabel = "Kaydet",
            onConfirm = { name ->
                callbacks.onRenameThread(target.id, name)
                renameThreadTarget = null
            },
            onDismiss = { renameThreadTarget = null },
        )
    }

    deleteThreadTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteThreadTarget = null },
            title = { Text("Sohbeti sil") },
            text = {
                Text(
                    "\"${target.title ?: "Sohbet"}\" ve içindeki ${target.messageCount} mesaj " +
                        "silinecek. (Kısa süreliğine geri alabilirsin.)",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    callbacks.onDeleteThread(target.id)
                    deleteThreadTarget = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteThreadTarget = null }) { Text("Vazgeç") }
            },
        )
    }

    moveThreadTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { moveThreadTarget = null },
            title = { Text("Klasöre taşı") },
            text = {
                Column {
                    TextButton(onClick = {
                        callbacks.onMoveToFolder(target.id, null)
                        moveThreadTarget = null
                    }) { Text(if (target.folderId == null) "✓ Klasörsüz" else "Klasörsüz") }
                    folders.forEach { folder ->
                        TextButton(onClick = {
                            callbacks.onMoveToFolder(target.id, folder.id)
                            moveThreadTarget = null
                        }) {
                            Text(if (target.folderId == folder.id) "✓ ${folder.name}" else folder.name)
                        }
                    }
                    TextButton(onClick = {
                        createFolderForThread = target.id
                        createFolderOpen = true
                        moveThreadTarget = null
                    }) { Text("+ Yeni klasör…") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveThreadTarget = null }) { Text("Vazgeç") }
            },
        )
    }

    if (createFolderOpen) {
        NameDialog(
            title = "Yeni klasör",
            initial = "",
            confirmLabel = "Oluştur",
            onConfirm = { name ->
                callbacks.onCreateFolder(name, createFolderForThread)
                createFolderOpen = false
                createFolderForThread = null
            },
            onDismiss = {
                createFolderOpen = false
                createFolderForThread = null
            },
        )
    }

    folderActionTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderActionTarget = null },
            title = { Text("\"${folder.name}\" klasörü") },
            text = { Text("Sohbetler silinmez — klasör silinirse Klasörsüz'e düşerler.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteFolderTarget = folder
                    folderActionTarget = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { folderActionTarget = null }) { Text("Vazgeç") }
                    TextButton(onClick = {
                        renameFolderNameTarget = folder
                        folderActionTarget = null
                    }) { Text("Yeniden Adlandır") }
                }
            },
        )
    }

    renameFolderNameTarget?.let { folder ->
        NameDialog(
            title = "Klasörü yeniden adlandır",
            initial = folder.name,
            confirmLabel = "Kaydet",
            onConfirm = { name ->
                callbacks.onRenameFolder(folder.id, name)
                renameFolderNameTarget = null
            },
            onDismiss = { renameFolderNameTarget = null },
        )
    }

    deleteFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text("Klasörü sil") },
            text = { Text("\"${folder.name}\" silinecek; içindeki sohbetler SİLİNMEZ, Klasörsüz'e taşınır.") },
            confirmButton = {
                TextButton(onClick = {
                    callbacks.onDeleteFolder(folder.id)
                    deleteFolderTarget = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderTarget = null }) { Text("Vazgeç") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    row: ThreadOverview,
    active: Boolean,
    folderBadge: String?,
    nowMs: Long,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = if (active) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (active) 0.dp else 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.pinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Sabitli",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp).height(14.dp),
                        )
                    }
                    Text(
                        row.title ?: "Sohbet",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        SessionListLogic.relativeTime(row.lastActivity, nowMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SessionListLogic.snippet(row.lastSnippet)?.let { snippet ->
                    Text(
                        snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${row.messageCount} mesaj",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    folderBadge?.let { badge ->
                        Text(
                            "· $badge",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Yeniden Adlandır") }, onClick = { menuOpen = false; onRename() })
            DropdownMenuItem(
                text = { Text(if (row.pinned) "Sabitlemeyi Kaldır" else "Sabitle") },
                onClick = { menuOpen = false; onTogglePin() },
            )
            DropdownMenuItem(text = { Text("Klasöre Taşı") }, onClick = { menuOpen = false; onMove() })
            DropdownMenuItem(
                text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}
