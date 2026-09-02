package com.yks2027.tracker.feature.aikoc

import com.yks2027.tracker.core.database.ThreadOverview
import java.util.Locale

/**
 * v1.3 — pure, unit-tested list logic for the sessions pane. Ordering and filtering
 * live here (not in SQL) so the rules are testable and the Turkish-aware title match
 * doesn't depend on SQLite's ASCII-only LOWER().
 */
object SessionListLogic {

    private val turkish = Locale.forLanguageTag("tr")

    /** Klasör filtresi: Tümü / Klasörsüz / belirli klasör. */
    sealed interface FolderFilter {
        data object All : FolderFilter
        data object Unfiled : FolderFilter
        data class Folder(val id: Long) : FolderFilter
    }

    /** Pinned first; within each group, last MESSAGE activity (newest first); id breaks ties. */
    fun sort(threads: List<ThreadOverview>): List<ThreadOverview> =
        threads.sortedWith(
            compareByDescending<ThreadOverview> { it.pinned }
                .thenByDescending { it.lastActivity }
                .thenByDescending { it.id },
        )

    /**
     * A non-blank query searches ACROSS all folders (results carry a folder badge);
     * with a blank query the folder chip filters. Title matching is Turkish-lowercased;
     * content matches arrive as thread ids from the DAO's LIKE query.
     */
    fun filter(
        threads: List<ThreadOverview>,
        folderFilter: FolderFilter,
        query: String,
        contentMatchIds: Set<Long>,
    ): List<ThreadOverview> {
        val trimmed = query.trim()
        val visible = if (trimmed.isEmpty()) {
            threads.filter { thread ->
                when (folderFilter) {
                    FolderFilter.All -> true
                    FolderFilter.Unfiled -> thread.folderId == null
                    is FolderFilter.Folder -> thread.folderId == folderFilter.id
                }
            }
        } else {
            val needle = trimmed.lowercase(turkish)
            threads.filter { thread ->
                thread.id in contentMatchIds ||
                    (thread.title ?: "").lowercase(turkish).contains(needle)
            }
        }
        return sort(visible)
    }

    /** "az önce" / "12 dk" / "5 sa" / "3 gün" — compact relative time for row metadata. */
    fun relativeTime(thenMs: Long, nowMs: Long): String {
        val minutes = ((nowMs - thenMs).coerceAtLeast(0)) / 60_000
        return when {
            minutes < 1 -> "az önce"
            minutes < 60 -> "$minutes dk"
            minutes < 48 * 60 -> "${minutes / 60} sa"
            else -> "${minutes / (24 * 60)} gün"
        }
    }

    /** One-line snippet: first non-blank line, hard-capped. */
    fun snippet(content: String?, max: Int = 80): String? =
        content?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(max)
}
