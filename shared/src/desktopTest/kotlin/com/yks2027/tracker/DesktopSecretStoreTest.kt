package com.yks2027.tracker

import com.yks2027.tracker.core.ai.DesktopSecretStore
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.0 — desktop SecretStore: AES-GCM file, per-installation key, owner-only permissions. */
class DesktopSecretStoreTest {

    private fun tempDir(): File = Files.createTempDirectory("yks-secrets").toFile()

    @Test
    fun roundTripAcrossInstances() = runBlocking {
        val dir = tempDir()
        val a = DesktopSecretStore(dir)
        a.setKey(7, "  sk-test-abc123  ")
        assertEquals("sk-test-abc123", a.getKey(7)) // trimmed like Android
        assertEquals(setOf(7L), a.profileIdsWithKey.first())
        // A fresh instance (next app launch) reads the same file with the same key.
        val b = DesktopSecretStore(dir)
        assertEquals("sk-test-abc123", b.getKey(7))
        assertEquals(setOf(7L), b.profileIdsWithKey.first())
        b.clearKey(7)
        assertNull(b.getKey(7))
        assertTrue(b.profileIdsWithKey.first().isEmpty())
    }

    @Test
    fun ciphertextIsNotPlaintextAndPerInstallationKeyMatters() = runBlocking {
        val dir = tempDir()
        DesktopSecretStore(dir).setKey(1, "sk-ant-secret")
        val stored = File(dir, "secrets.properties").readText()
        assertFalse(stored.contains("sk-ant-secret"))
        // Replacing the installation key makes existing entries undecryptable (returns null, never garbage).
        File(dir, "secret.key").writeBytes(ByteArray(32) { 0x42 })
        assertNull(DesktopSecretStore(dir).getKey(1))
    }

    @Test
    fun filesAreOwnerOnlyOnPosix() = runBlocking {
        val dir = tempDir()
        DesktopSecretStore(dir).setKey(3, "k")
        if (!System.getProperty("os.name").lowercase().contains("win")) {
            for (name in listOf("secret.key", "secrets.properties")) {
                val perms = Files.getPosixFilePermissions(File(dir, name).toPath())
                assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms)
            }
        }
    }

    @Test
    fun noLegacySlotOnDesktop() = runBlocking {
        val store = DesktopSecretStore(tempDir())
        assertFalse(store.hasLegacyKey())
        store.migrateLegacyKeyTo(1) // no-op, must not throw
    }
}
