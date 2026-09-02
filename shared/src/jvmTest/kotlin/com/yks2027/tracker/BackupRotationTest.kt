package com.yks2027.tracker

import com.yks2027.tracker.core.backup.BackupRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.0 — auto-backup rotation is shared by SAF (Android) and plain directories (desktop). */
class BackupRotationTest {

    @Test
    fun keepsNewestEightAndIgnoresForeignFiles() {
        val names = (1..10).map { "yks_backup_2026-08-%02d.json".format(it) } +
            listOf("notes.txt", "yks_backup_bad.json", "pre_import_snapshot.json", "yks_denemeler_2026-08-30.csv")
        val stale = BackupRotation.staleBackupNames(names.shuffled(), keep = 8)
        // Only real backup names, oldest first dropped; foreign files never touched.
        assertEquals(listOf("yks_backup_2026-08-02.json", "yks_backup_2026-08-01.json"), stale)
    }

    @Test
    fun nothingStaleBelowTheLimit() {
        val names = (1..8).map { "yks_backup_2026-08-%02d.json".format(it) }
        assertTrue(BackupRotation.staleBackupNames(names, keep = 8).isEmpty())
    }

    @Test
    fun backupNameShapeIsPlatformIndependent() {
        // The exact shape BackupManager.suggestedFileName() produces on every platform.
        assertTrue(BackupRotation.isBackupName("yks_backup_2026-09-01.json"))
        assertFalse(BackupRotation.isBackupName("yks_backup_2026-9-1.json"))
        assertFalse(BackupRotation.isBackupName("YKS_backup_2026-09-01.json"))
        assertFalse(BackupRotation.isBackupName("yks_backup_2026-09-01.json.bak"))
    }
}
