package com.aethercam.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.security.MessageDigest

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
        private const val TAG = "SecurityManager"
        private const val PREFS_NAME = "aethercam_secure_prefs"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SAVED_SERVER_KEY = "server_pub_key"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences init failed, falling back to private SharedPreferences: ${e.message}")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun parseQrCodePayload(rawQrString: String): PairingInfo? {
        return try {
            val json = JSONObject(rawQrString)
            PairingInfo(
                version = json.optInt("v", 2),
                hostIp = json.getString("ip"),
                udpPort = json.optInt("udpPort", 48200),
                tcpPort = json.optInt("tcpPort", 48201),
                pin = json.getString("pin"),
                serverPublicKey = json.optString("serverPubKey", ""),
                hostname = json.optString("hostname", "AetherCam Host")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing QR payload", e)
            null
        }
    }

    fun verifyPinHash(pin: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(digest, Base64.NO_WRAP)
        } catch (e: Exception) {
            pin
        }
    }

    fun saveSessionToken(token: String, serverKey: String) {
        try {
            prefs.edit()
                .putString(KEY_SESSION_TOKEN, token)
                .putString(KEY_SAVED_SERVER_KEY, serverKey)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving session token", e)
        }
    }

    fun getSessionToken(): String? {
        return try {
            prefs.getString(KEY_SESSION_TOKEN, null)
        } catch (e: Exception) {
            null
        }
    }
}
