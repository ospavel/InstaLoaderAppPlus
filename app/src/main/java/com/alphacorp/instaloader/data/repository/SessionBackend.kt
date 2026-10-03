package com.alphacorp.instaloader.data.repository

import com.alphacorp.instaloader.data.model.InstagramSession
import java.io.File

interface SessionBackend {
    fun verifyBrowserSession(cookieHeader: String, userAgent: String): InstagramSession
    fun readEncryptedSession(): InstagramSession?
    fun legacyUsername(): String?
    fun legacySessionFile(username: String, sessionDir: String): File
    fun legacySessionExists(sessionFile: File): Boolean
    fun legacySessionJson(username: String, sessionFile: File): InstagramSession
    fun saveEncryptedSession(session: InstagramSession): Boolean
    fun clearEncryptedSession()
    fun deleteLegacySession(sessionFile: File)
    fun deleteLegacySessions(sessionDir: String? = null)
}
