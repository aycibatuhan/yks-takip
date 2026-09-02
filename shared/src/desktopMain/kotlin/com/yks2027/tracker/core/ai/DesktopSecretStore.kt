package com.yks2027.tracker.core.ai

import com.yks2027.tracker.core.platform.SecretStore
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Desktop SecretStore (spec §3): AES-GCM-encrypted `secrets.properties` with a random
 * 256-bit key generated once per installation in `secret.key`, both owner-only
 * (rw-------) where the file system supports POSIX permissions.
 *
 * ⚠️ Weaker than Android Keystore: anyone with read access to the user's profile can
 * decrypt. macOS Keychain / Windows DPAPI integration is deferred (README).
 */
class DesktopSecretStore(private val dir: File) : SecretStore {

    private val keyFile = File(dir, "secret.key")
    private val storeFile = File(dir, "secrets.properties")
    private val lock = Mutex()
    private val ids = MutableStateFlow(readAll().keys)

    override val profileIdsWithKey: Flow<Set<Long>> = ids.asStateFlow()

    override suspend fun getKey(profileId: Long): String? = withContext(Dispatchers.IO) {
        lock.withLock { readAll()[profileId]?.let { runCatching { decrypt(it) }.getOrNull() } }
    }

    override suspend fun setKey(profileId: Long, apiKey: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val all = readAll().toMutableMap()
            all[profileId] = encrypt(apiKey.trim())
            writeAll(all)
        }
    }

    override suspend fun clearKey(profileId: Long) = withContext(Dispatchers.IO) {
        lock.withLock {
            val all = readAll().toMutableMap()
            if (all.remove(profileId) != null) writeAll(all)
        }
    }

    override suspend fun hasLegacyKey(): Boolean = false
    override suspend fun migrateLegacyKeyTo(profileId: Long) = Unit

    // --- file format: one `profileId=base64(iv+ciphertext)` per line ---

    private fun readAll(): Map<Long, String> {
        if (!storeFile.exists()) return emptyMap()
        return storeFile.readLines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).toLongOrNull()?.let { it to line.substring(i + 1) }
        }.toMap()
    }

    private fun writeAll(all: Map<Long, String>) {
        dir.mkdirs()
        storeFile.writeText(all.entries.joinToString("\n") { "${it.key}=${it.value}" })
        ownerOnly(storeFile)
        ids.value = all.keys
    }

    private fun secretKey(): SecretKeySpec {
        val raw = if (keyFile.exists()) {
            keyFile.readBytes()
        } else {
            dir.mkdirs()
            ByteArray(32).also { SecureRandom().nextBytes(it) }.also { keyFile.writeBytes(it); ownerOnly(keyFile) }
        }
        return SecretKeySpec(raw, "AES")
    }

    private fun encrypt(plain: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    private fun decrypt(stored: String): String {
        val payload = Base64.getDecoder().decode(stored)
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(payload.copyOfRange(GCM_IV_BYTES, payload.size)).toString(Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        fun ownerOnly(file: File) {
            runCatching {
                Files.setPosixFilePermissions(
                    file.toPath(),
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            } // Windows: no POSIX ACL API here; the profile directory is already user-private.
        }
    }
}
