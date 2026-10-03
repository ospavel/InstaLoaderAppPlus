package com.alphacorp.instaloader.data.repository

import android.content.Context
import com.alphacorp.instaloader.data.local.EncryptedSessionStore
import com.alphacorp.instaloader.data.model.InstagramSession
import com.alphacorp.instaloader.data.python.PythonBridge
import java.io.File

class AndroidSessionBackend(
    context: Context,
    private val pythonBridge: PythonBridge,
    private val sessionStore: EncryptedSessionStore,
) : SessionBackend {

    private val appContext = context.applicationContext

    override fun verifyBrowserSession(cookieHeader: String, userAgent: String): InstagramSession =
        pythonBridge.verifyBrowserSession(cookieHeader, userAgent)

    override fun readEncryptedSession(): InstagramSession? = sessionStore.session

    override fun legacyUsername(): String? = sessionStore.username

    override fun legacySessionFile(username: String, sessionDir: String): File =
        File(sessionDir, "$SESSION_FILE_PREFIX$username")

    override fun legacySessionExists(sessionFile: File): Boolean = sessionFile.isFile

    override fun legacySessionJson(username: String, sessionFile: File): InstagramSession =
        pythonBridge.legacySessionJson(username, sessionFile.absolutePath)

    override fun saveEncryptedSession(session: InstagramSession): Boolean =
        sessionStore.saveSession(session)

    override fun clearEncryptedSession() {
        sessionStore.clearSession()
    }

    override fun deleteLegacySession(sessionFile: File) {
        if (sessionFile.isFile) sessionFile.delete()
    }

    override fun deleteLegacySessions(sessionDir: String?) {
        val sessionDirectory = sessionDir?.let(::File)
            ?: sessionStore.sessionDir?.let(::File)
            ?: File(appContext.filesDir, SESSION_DIR_NAME)
        if (!sessionDirectory.isDirectory) return
        sessionDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(SESSION_FILE_PREFIX) }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val SESSION_DIR_NAME = "sessions"
        const val SESSION_FILE_PREFIX = "session-"
    }
}
