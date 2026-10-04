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

object SecureConfig{
    private const val PREFS="superjet_secure_config";private const val SERVER="server_url";private const val TOKEN="staff_token"
    private const val KS="AndroidKeyStore";private const val ALIAS="SuperJetStaffSession"
    fun setServerUrl(c:Context,v:String){c.getSharedPreferences(PREFS,0).edit().putString(SERVER,v.trim().removeSuffix("/")).apply()}
    fun getServerUrl(c:Context):String=c.getSharedPreferences(PREFS,0).getString(SERVER,"")?.trim()?.removeSuffix("/")?.ifBlank{BuildConfig.SUPERJET_BASE_URL.trimEnd('/')}?:BuildConfig.SUPERJET_BASE_URL.trimEnd('/')
    fun setToken(c:Context,v:String){if(v.isBlank()){clearToken(c);return};c.getSharedPreferences(PREFS,0).edit().putString(TOKEN,encrypt(v.trim())).apply()}
    fun getToken(c:Context):String=runCatching{val v=c.getSharedPreferences(PREFS,0).getString(TOKEN,"")?:"";if(v.isBlank())"" else decrypt(v)}.getOrDefault("")
    fun clearToken(c:Context){c.getSharedPreferences(PREFS,0).edit().remove(TOKEN).apply()}
    private fun key():SecretKey{
        val ks=KeyStore.getInstance(KS).apply{load(null)};(ks.getKey(ALIAS,null) as? SecretKey)?.let{return it}
        val g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,KS);g.init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey()
    }
    private fun encrypt(v:String):String{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.iv+c.doFinal(v.toByteArray(StandardCharsets.UTF_8)),Base64.NO_WRAP)}
    private fun decrypt(v:String):String{val raw=Base64.decode(v,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,raw.copyOfRange(0,12)));return String(c.doFinal(raw.copyOfRange(12,raw.size)),StandardCharsets.UTF_8)}
}