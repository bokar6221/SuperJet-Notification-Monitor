package com.superjet.notificationmonitor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureConfig {
    private const val PREFS = "superjet_secure_config"
    private const val KEY_SERVER = "server_url"
    private const val KEY_TOKEN = "android_token"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SuperJetNotificationToken"

    fun setServerUrl(context: Context, value: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY_SERVER, value.trim().removeSuffix("/"))
            .apply()
    }

    fun getServerUrl(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString(KEY_SERVER, "") ?: ""

    fun setToken(context: Context, value: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY_TOKEN, encrypt(value.trim()))
            .apply()
    }

    fun getToken(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, 0).getString(KEY_TOKEN, "") ?: ""
        if (stored.isBlank()) return ""
        return runCatching { decrypt(stored) }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val output = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(output, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val raw = Base64.decode(value, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val data = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, iv)
        )
        return String(cipher.doFinal(data), StandardCharsets.UTF_8)
    }
}
