package com.yks2027.tracker

import com.yks2027.tracker.core.backup.BackupManager
import com.yks2027.tracker.core.database.ThreadOverview
import com.yks2027.tracker.feature.aikoc.SessionListLogic
import com.yks2027.tracker.feature.aikoc.SessionListLogic.FolderFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v1.3 — sessions pane ordering, search/filter, and folder-orphan semantics. */
class SessionListLogicTest {

    private fun thread(
        id: Long,
        title: String? = "Sohbet $id",
        pinned: Boolean = false,
        folderId: Long? = null,
        lastActivity: Long = id * 1000,
        createdAt: Long = id,
    ) = ThreadOverview(
        id = id, title = title, createdAt = createdAt, pinned = pinned,
        folderId = folderId, messageCount = 2, lastActivity = lastActivity, lastSnippet = null,
    )

    // --- ordering ---

    @Test
    fun pinnedThreadsSortFirstRegardlessOfActivity() {
        val sorted = SessionListLogic.sort(
            listOf(
                thread(1, lastActivity = 9_000), // most recent, not pinned
                thread(2, pinned = true, lastActivity = 1_000), // old but pinned
                thread(3, lastActivity = 5_000),
            ),
        )
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun withinPinGroupsLastMessageActivityWinsNotCreation() {
        val sorted = SessionListLogic.sort(
            listOf(
                thread(1, createdAt = 100, lastActivity = 500), // created FIRST, active LAST
                thread(2, createdAt = 200, lastActivity = 300),
            ),
        )
        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test
    fun equalActivityBreaksTiesByNewestId() {
        val sorted = SessionListLogic.sort(listOf(thread(1, lastActivity = 42), thread(2, lastActivity = 42)))
        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    // --- folder filtering (blank query) ---

    @Test
    fun folderFilterAllUnfiledAndSpecific() {
        val threads = listOf(thread(1, folderId = null), thread(2, folderId = 7), thread(3, folderId = 8))
        assertEquals(3, SessionListLogic.filter(threads, FolderFilter.All, "", emptySet()).size)
        assertEquals(
            listOf(1L),
            SessionListLogic.filter(threads, FolderFilter.Unfiled, "", emptySet()).map { it.id },
        )
        assertEquals(
            listOf(2L),
            SessionListLogic.filter(threads, FolderFilter.Folder(7), "", emptySet()).map { it.id },
        )
    }

    @Test
    fun folderDeleteOrphansShowUpUnderUnfiled() {
        // After a folder delete the FK sets folder_id NULL — the Unfiled view must own them.
        val afterDelete = listOf(thread(1, folderId = null), thread(2, folderId = null), thread(3, folderId = 9))
        assertEquals(
            listOf(2L, 1L),
            SessionListLogic.filter(afterDelete, FolderFilter.Unfiled, "", emptySet()).map { it.id },
        )
    }

    // --- search ---

    @Test
    fun searchMatchesTitleTurkishCaseInsensitively() {
        val threads = listOf(thread(1, title = "İNTEGRAL soruları"), thread(2, title = "Motivasyon"))
        val hits = SessionListLogic.filter(threads, FolderFilter.All, "integral", emptySet())
        assertEquals(listOf(1L), hits.map { it.id })
    }

    @Test
    fun searchUnionsTitleAndContentMatches() {
        val threads = listOf(thread(1, title = "Fizik planı"), thread(2, title = "Başka"), thread(3, title = "Alakasız"))
        val hits = SessionListLogic.filter(threads, FolderFilter.All, "fizik", contentMatchIds = setOf(2L))
        assertEquals(setOf(1L, 2L), hits.map { it.id }.toSet())
    }

    @Test
    fun searchIgnoresFolderFilterAndCrossesFolders() {
        // Result rows carry a folder badge precisely because search is cross-folder.
        val threads = listOf(thread(1, title = "Türev", folderId = 7), thread(2, title = "Türev tekrar", folderId = 8))
        val hits = SessionListLogic.filter(threads, FolderFilter.Folder(7), "türev", emptySet())
        assertEquals(2, hits.size)
    }

    @Test
    fun blankQueryAfterTrimFallsBackToFolderFilter() {
        val threads = listOf(thread(1, folderId = null), thread(2, folderId = 7))
        val hits = SessionListLogic.filter(threads, FolderFilter.Unfiled, "   ", setOf(2L))
        assertEquals(listOf(1L), hits.map { it.id })
    }

    // --- helpers ---

    @Test
    fun relativeTimeBuckets() {
        val now = 100_000_000L
        assertEquals("az önce", SessionListLogic.relativeTime(now - 30_000, now))
        assertEquals("12 dk", SessionListLogic.relativeTime(now - 12 * 60_000, now))
        assertEquals("5 sa", SessionListLogic.relativeTime(now - 5 * 3_600_000, now))
        assertEquals("3 gün", SessionListLogic.relativeTime(now - 3 * 86_400_000, now))
    }

    @Test
    fun snippetTakesFirstNonBlankLineCapped() {
        assertEquals("Merhaba", SessionListLogic.snippet("\n\nMerhaba\nikinci satır"))
        assertEquals(80, SessionListLogic.snippet("x".repeat(500))!!.length)
        assertNull(SessionListLogic.snippet(null))
    }

    // --- restore orphan guard (mirrors the live FK SET NULL) ---

    @Test
    fun restoredThreadWithMissingFolderFallsBackToUnfiled() {
        assertEquals(7L, BackupManager.resolveRestoredFolderId(7L, setOf(7L, 8L)))
        assertNull(BackupManager.resolveRestoredFolderId(9L, setOf(7L, 8L)))
        assertNull(BackupManager.resolveRestoredFolderId(null, setOf(7L)))
    }
}
