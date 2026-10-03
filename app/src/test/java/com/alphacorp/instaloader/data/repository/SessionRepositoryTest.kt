package com.alphacorp.instaloader.data.repository

import com.alphacorp.instaloader.data.model.InstagramSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionRepositoryTest {

    @Test
    fun importingAnotherAccountReplacesTheWholeSessionAfterVerification() = runBlocking {
        val previous = session("alice", "old-cookie", "old-ua")
        val replacement = session("bob", "new-cookie", "new-ua")
        val backend = FakeSessionBackend().apply {
            storedSession = previous
            verifiedSession = replacement
            legacyUsernameValue = "alice"
        }

        val result = SessionRepository(backend).importBrowserSession(
            cookieHeader = "sessionid=browser-cookie",
            userAgent = "new-ua",
            sessionDir = "/sessions",
        )

        assertEquals(replacement, result)
        assertEquals(replacement, backend.storedSession)
        assertEquals(listOf("verify", "save", "delete-legacy"), backend.events)
    }

    @Test
    fun failedVerificationLeavesExistingSessionAndLegacyFilesUntouched() = runBlocking {
        val previous = session("alice", "old-cookie", "old-ua")
        val backend = FakeSessionBackend().apply {
            storedSession = previous
            verificationFailure = IllegalStateException("Instagram refused the session")
        }

        val failure = runCatching {
            SessionRepository(backend).importBrowserSession("sessionid=input", "UA", "/sessions")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(backend.verificationFailure?.message, failure?.message)
        assertEquals(previous, backend.storedSession)
        assertEquals(listOf("verify"), backend.events)
    }

    @Test
    fun migrationSavesEncryptedSessionBeforeDeletingLegacyFile() = runBlocking {
        val migrated = session("alice", "legacy-cookie", "")
        val backend = FakeSessionBackend().apply {
            legacyUsernameValue = "alice"
            legacySessionValue = migrated
            legacyFileExists = true
        }

        val result = SessionRepository(backend).currentSession("/sessions")

        assertEquals(migrated, result)
        assertEquals(listOf("convert-legacy", "save", "delete-legacy-file"), backend.events)
        assertFalse(backend.legacyFileExists)
    }

    @Test
    fun failedEncryptedMigrationKeepsLegacyFileForRetry() = runBlocking {
        val backend = FakeSessionBackend().apply {
            legacyUsernameValue = "alice"
            legacySessionValue = session("alice", "legacy-cookie", "")
            legacyFileExists = true
            saveResult = false
        }

        assertNull(SessionRepository(backend).currentSession("/sessions"))

        assertTrue(backend.legacyFileExists)
        assertEquals(listOf("convert-legacy", "save"), backend.events)
    }

    @Test
    fun unreadableLegacyMigrationClearsStaleUsernameButPreservesFile() = runBlocking {
        val backend = FakeSessionBackend().apply {
            legacyUsernameValue = "alice"
            legacyFileExists = true
            legacyFailure = IllegalArgumentException("bad pickle")
        }

        assertNull(SessionRepository(backend).currentSession("/sessions"))

        assertNull(backend.legacyUsernameValue)
        assertTrue(backend.legacyFileExists)
        assertEquals(listOf("convert-legacy", "clear-encrypted"), backend.events)
    }

    @Test
    fun encryptedSessionSkipsLegacyMigration() = runBlocking {
        val saved = session("alice", "encrypted-cookie", "encrypted-ua")
        val backend = FakeSessionBackend().apply {
            storedSession = saved
            legacyUsernameValue = "alice"
            legacyFileExists = true
        }

        assertEquals(saved, SessionRepository(backend).currentSession("/sessions"))
        assertTrue(backend.events.isEmpty())
    }

    private class FakeSessionBackend : SessionBackend {
        var storedSession: InstagramSession? = null
        var legacyUsernameValue: String? = null
        var verifiedSession: InstagramSession = session("verified", "cookie", "ua")
        var legacySessionValue: InstagramSession = session("legacy", "cookie", "")
        var verificationFailure: Exception? = null
        var legacyFailure: Exception? = null
        var legacyFileExists = false
        var saveResult = true
        val events = mutableListOf<String>()

        override fun verifyBrowserSession(cookieHeader: String, userAgent: String): InstagramSession {
            events += "verify"
            verificationFailure?.let { throw it }
            return verifiedSession
        }

        override fun readEncryptedSession(): InstagramSession? = storedSession

        override fun legacyUsername(): String? = legacyUsernameValue

        override fun legacySessionFile(username: String, sessionDir: String): File =
            File(sessionDir, "session-$username")

        override fun legacySessionExists(sessionFile: File): Boolean = legacyFileExists

        override fun legacySessionJson(username: String, sessionFile: File): InstagramSession {
            events += "convert-legacy"
            legacyFailure?.let { throw it }
            return legacySessionValue
        }

        override fun saveEncryptedSession(session: InstagramSession): Boolean {
            events += "save"
            if (saveResult) storedSession = session
            return saveResult
        }

        override fun clearEncryptedSession() {
            events += "clear-encrypted"
            storedSession = null
            legacyUsernameValue = null
        }

        override fun deleteLegacySession(sessionFile: File) {
            events += "delete-legacy-file"
            legacyFileExists = false
        }

        override fun deleteLegacySessions(sessionDir: String?) {
            events += "delete-legacy"
            legacyFileExists = false
        }
    }

    private companion object {
        fun session(username: String, cookie: String, userAgent: String) =
            InstagramSession(username, "{\"sessionid\":\"$cookie\"}", userAgent)
    }
}
