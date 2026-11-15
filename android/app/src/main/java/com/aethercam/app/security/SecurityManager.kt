package com.aethercam.app.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

data class PairingInfo(
    val version: Int,
    val hostIp: String,
    val udpPort: Int,
    val tcpPort: Int,
    val pin: String,
    val serverPublicKey: String,
    val hostname: String
)

class SecurityManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "aethercam_secure_prefs"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SAVED_SERVER_KEY = "server_pub_key"
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val securePrefs = EncryptedSharedPreferences.create(
        PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun parseQrCodePayload(rawQrString: String): PairingInfo? {
        return try {
            val json = JSONObject(rawQrString)
            PairingInfo(
                version = json.optInt("v", 2),
                hostIp = json.getString("ip"),
                udpPort = json.optInt("udpPort", 48200),
                tcpPort = json.optInt("tcpPort", 48201),
                pin = json.getString("pin"),
                serverPublicKey = json.getString("serverPubKey"),
                hostname = json.optString("hostname", "AetherCam Host")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun verifyPinHash(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    fun saveSessionToken(token: String, serverKey: String) {
        securePrefs.edit()
            .putString(KEY_SESSION_TOKEN, token)
            .putString(KEY_SAVED_SERVER_KEY, serverKey)
            .apply()
    }

    fun getSessionToken(): String? {
        return securePrefs.getString(KEY_SESSION_TOKEN, null)
    }
}
