package com.yks2027.tracker.core.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yks2027.tracker.core.platform.SecretStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * PRD §8 — the AI API key lives encrypted at rest (Android Keystore AES-GCM; the key
 * material never leaves the Keystore), in its own `ai_secrets` store, and is deliberately
 * EXCLUDED from JSON backups (re-enter after an import).
 *
 * v2.0: the v1.x AiSecretsRepository behind the shared SecretStore contract. Key alias,
 * transformation, IV layout and DataStore keys are UNCHANGED so keys saved by v1.x decrypt
 * after the in-place update.
 */
class AndroidKeystoreSecretStore(private val store: DataStore<Preferences>) : SecretStore {

    private object Keys {
        /** Legacy v1.0/v1.1 single-slot key; migrated into a per-profile entry once. */
        val LEGACY_API_KEY = stringPreferencesKey("api_key_encrypted")

        fun forProfile(profileId: Long): Preferences.Key<String> =
            stringPreferencesKey("api_key_encrypted_$profileId")
    }

    override val profileIdsWithKey: Flow<Set<Long>> = store.data.map { prefs ->
        prefs.asMap().keys.mapNotNull { key ->
            key.name.removePrefix("api_key_encrypted_").takeIf { it != key.name }?.toLongOrNull()
        }.toSet()
    }

    override suspend fun getKey(profileId: Long): String? =
        store.data.first()[Keys.forProfile(profileId)]?.let { stored ->
            runCatching { decrypt(stored) }.getOrNull()
        }

    override suspend fun setKey(profileId: Long, apiKey: String) {
        val encrypted = encrypt(apiKey.trim())
        store.edit { it[Keys.forProfile(profileId)] = encrypted }
    }

    override suspend fun clearKey(profileId: Long) {
        store.edit { it.remove(Keys.forProfile(profileId)) }
    }

    override suspend fun hasLegacyKey(): Boolean =
        store.data.first()[Keys.LEGACY_API_KEY] != null

    /** Re-keys the stored ciphertext to the profile entry without ever decrypting to UI. */
    override suspend fun migrateLegacyKeyTo(profileId: Long) {
        store.edit { prefs ->
            prefs[Keys.LEGACY_API_KEY]?.let { stored ->
                prefs[Keys.forProfile(profileId)] = stored
                prefs.remove(Keys.LEGACY_API_KEY)
            }
        }
    }

    // --- Keystore AES-GCM ---

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext // IV is 12 bytes for GCM
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val payload = Base64.decode(stored, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "yks_ai_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
