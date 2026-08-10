package com.udnahc.opentasks.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidKeystoreAuthTokenStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthTokenStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun readActiveToken(): String? = readToken(ACTIVE_KEY)

    override suspend fun writeActiveToken(token: String) = writeToken(ACTIVE_KEY, token)

    override suspend fun clearActiveToken() = clearToken(ACTIVE_KEY)

    override suspend fun readPendingToken(): String? = readToken(PENDING_KEY)

    override suspend fun writePendingToken(token: String) = writeToken(PENDING_KEY, token)

    override suspend fun clearPendingToken() = clearToken(PENDING_KEY)

    override suspend fun promotePendingToken() {
        withContext(ioDispatcher) {
            val pending = readEncryptedToken(PENDING_KEY) ?: return@withContext
            val active = encrypt(pending)
            if (!preferences.edit().putString(ACTIVE_KEY, active).commit()) {
                throw SecureTokenStoreException("Android auth token promotion was not committed")
            }
            if (!preferences.edit().remove(PENDING_KEY).commit()) {
                throw SecureTokenStoreException("Android pending auth token cleanup was not committed")
            }
        }
    }

    override suspend fun clearAllTokens() {
        withContext(ioDispatcher) {
            if (!preferences.edit().remove(ACTIVE_KEY).remove(PENDING_KEY).commit()) {
                throw SecureTokenStoreException("Android auth token cleanup was not committed")
            }
        }
    }

    private suspend fun readToken(key: String): String? = withContext(ioDispatcher) {
        readEncryptedToken(key)
    }

    private suspend fun writeToken(key: String, token: String) {
        require(token.isNotBlank()) { "Auth token must not be blank" }
        withContext(ioDispatcher) {
            if (!preferences.edit().putString(key, encrypt(token)).commit()) {
                throw SecureTokenStoreException("Android auth token write was not committed")
            }
        }
    }

    private suspend fun clearToken(key: String) {
        withContext(ioDispatcher) {
            if (!preferences.edit().remove(key).commit()) {
                throw SecureTokenStoreException("Android auth token cleanup was not committed")
            }
        }
    }

    private fun readEncryptedToken(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        val parts = encoded.split(DELIMITER)
        if (parts.size != 2) {
            throw SecureTokenStoreException("Android auth token record is invalid")
        }
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyStoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext).decodeToString().takeIf { it.isNotBlank() }
                ?: throw SecureTokenStoreException("Android auth token record is empty")
        } catch (error: SecureTokenStoreException) {
            throw error
        } catch (error: Throwable) {
            throw SecureTokenStoreException("Android auth token could not be decrypted", error)
        }
    }

    private fun encrypt(token: String): String {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyStoreKey())
            val ciphertext = cipher.doFinal(token.encodeToByteArray())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encodedCiphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            return "$iv$DELIMITER$encodedCiphertext"
        } catch (error: Throwable) {
            throw SecureTokenStoreException("Android auth token encryption failed", error)
        }
    }

    private fun keyStoreKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            return generator.generateKey()
        } catch (error: Throwable) {
            throw SecureTokenStoreException("Android Keystore auth key is unavailable", error)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "opentasks_account_tokens"
        const val ACTIVE_KEY = "active"
        const val PENDING_KEY = "pending"
        const val KEY_ALIAS = "opentasks_account_tokens_aes"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val DELIMITER = "."
    }
}
