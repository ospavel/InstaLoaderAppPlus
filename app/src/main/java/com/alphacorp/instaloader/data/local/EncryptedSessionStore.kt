package com.alphacorp.instaloader.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alphacorp.instaloader.data.model.InstagramSession

class EncryptedSessionStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    val session: InstagramSession?
        get() {
            val username = prefs.getString(KEY_USERNAME, null) ?: return null
            val cookiesJson = prefs.getString(KEY_COOKIES_JSON, null) ?: return null
            return InstagramSession(
                username = username,
                cookiesJson = cookiesJson,
                userAgent = prefs.getString(KEY_USER_AGENT, "").orEmpty(),
            )
        }

    val sessionDir: String?
        get() = prefs.getString(KEY_SESSION_DIR, null)

    fun initializeSessionDir(path: String) {
        prefs.edit().putString(KEY_SESSION_DIR, path).apply()
    }

    fun saveSession(session: InstagramSession): Boolean = prefs.edit()
        .putString(KEY_USERNAME, session.username)
        .putString(KEY_COOKIES_JSON, session.cookiesJson)
        .putString(KEY_USER_AGENT, session.userAgent)
        .commit()

    fun clearSession(): Boolean = prefs.edit()
        .remove(KEY_USERNAME)
        .remove(KEY_COOKIES_JSON)
        .remove(KEY_USER_AGENT)
        .commit()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "instaloader_session"
        private const val KEY_USERNAME = "username"
        private const val KEY_SESSION_DIR = "session_dir"
        private const val KEY_COOKIES_JSON = "cookies_json"
        private const val KEY_USER_AGENT = "user_agent"
    }
}
