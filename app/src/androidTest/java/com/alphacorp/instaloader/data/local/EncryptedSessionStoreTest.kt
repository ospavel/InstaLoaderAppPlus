package com.alphacorp.instaloader.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alphacorp.instaloader.data.model.InstagramSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedSessionStoreTest {

    @Test
    fun replacesAndClearsEncryptedSessionWithoutPlaintextCookieValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedSessionStore(context)
        val sessionDir = File(context.filesDir, "sessions").absolutePath
        val first = InstagramSession("alice", "{\"sessionid\":\"alice-private-cookie\"}", "UA Alice")
        val replacement = InstagramSession("bob", "{\"sessionid\":\"bob-private-cookie\"}", "UA Bob")

        store.clearSession()
        store.initializeSessionDir(sessionDir)
        assertTrue(store.saveSession(first))
        assertTrue(store.saveSession(replacement))

        assertEquals(replacement, store.session)
        val encryptedPreferences = File(context.applicationInfo.dataDir, "shared_prefs/instaloader_session.xml")
        val rawContents = encryptedPreferences.readText()
        assertFalse(rawContents.contains("alice-private-cookie"))
        assertFalse(rawContents.contains("bob-private-cookie"))

        store.clearSession()

        assertNull(store.session)
        assertEquals(sessionDir, store.sessionDir)
    }
}
