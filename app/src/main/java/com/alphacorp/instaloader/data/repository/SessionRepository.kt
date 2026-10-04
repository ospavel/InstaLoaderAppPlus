package com.alphacorp.instaloader.data.repository

import android.content.Context
import com.alphacorp.instaloader.data.local.EncryptedSessionStore
import com.alphacorp.instaloader.data.model.InstagramSession
import com.alphacorp.instaloader.data.python.PythonBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionRepository(private val backend: SessionBackend) {

    constructor(
        context: Context,
        pythonBridge: PythonBridge,
        sessionStore: EncryptedSessionStore,
    ) : this(AndroidSessionBackend(context, pythonBridge, sessionStore))

    val username: String?
        get() = backend.readEncryptedSession()?.username ?: backend.legacyUsername()

    suspend fun importBrowserSession(
        cookieHeader: String,
        userAgent: String,
        sessionDir: String,
    ): InstagramSession = withContext(Dispatchers.IO) {
        val imported = backend.verifyBrowserSession(cookieHeader, userAgent)
        if (imported.username.isBlank() || imported.cookiesJson.isBlank()) {
            throw IllegalStateException("Instagram did not return a complete login session.")
        }
        if (!backend.saveEncryptedSession(imported)) {
            throw IllegalStateException("The Instagram session could not be saved securely.")
        }
        backend.deleteLegacySessions(sessionDir)
        imported
    }

    suspend fun currentSession(sessionDir: String): InstagramSession? = withContext(Dispatchers.IO) {
        backend.readEncryptedSession()?.let { return@withContext it }

        val legacyUsername = backend.legacyUsername() ?: return@withContext null
        val legacyFile = backend.legacySessionFile(legacyUsername, sessionDir)
        if (!backend.legacySessionExists(legacyFile)) {
            backend.clearEncryptedSession()
            return@withContext null
        }

        val converted = try {
            backend.legacySessionJson(legacyUsername, legacyFile)
        } catch (_: Exception) {
            backend.clearEncryptedSession()
            return@withContext null
        }

        if (converted.username != legacyUsername || converted.cookiesJson.isBlank()) {
            backend.clearEncryptedSession()
            return@withContext null
        }

        val migrated = converted.copy(userAgent = "")
        if (!backend.saveEncryptedSession(migrated)) {
            return@withContext null
        }
        backend.deleteLegacySession(legacyFile)
        migrated
    }

    fun logout() {
        backend.deleteLegacySessions()
        backend.clearEncryptedSession()
    }
}
