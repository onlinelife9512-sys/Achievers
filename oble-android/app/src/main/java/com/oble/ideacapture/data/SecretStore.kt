package com.oble.ideacapture.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores API keys / tokens encrypted with an AES-256-GCM key that lives in the
 * Android Keystore (the key material never leaves secure hardware where available).
 * Nothing secret is ever written in plain text or committed to the repository.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("oble_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + encrypted
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, IV_SIZE)
            val data = blob.copyOfRange(IV_SIZE, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "oble_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12

        const val GEMINI_API_KEY = "gemini_api_key"
        const val WA_CLOUD_TOKEN = "wa_cloud_token"
    }
}
