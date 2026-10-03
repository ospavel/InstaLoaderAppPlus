package com.alphacorp.instaloader.util

import com.alphacorp.instaloader.util.InstagramLoginUrlPolicy.Destination
import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramLoginUrlPolicyTest {

    @Test
    fun instagramAndMetaHostsStayInsideTheWebView() {
        assertEquals(Destination.WEBVIEW, InstagramLoginUrlPolicy.classify("https://www.instagram.com/"))
        assertEquals(Destination.WEBVIEW, InstagramLoginUrlPolicy.classify("https://instagram.com/accounts/login/"))
        assertEquals(Destination.WEBVIEW, InstagramLoginUrlPolicy.classify("https://accountscenter.facebook.com/"))
        assertEquals(Destination.WEBVIEW, InstagramLoginUrlPolicy.classify("https://facebook.com/"))
    }

    @Test
    fun otherValidHttpsHostsAreExternal() {
        assertEquals(Destination.EXTERNAL_HTTPS, InstagramLoginUrlPolicy.classify("https://help.example.com/"))
        assertEquals(Destination.EXTERNAL_HTTPS, InstagramLoginUrlPolicy.classify("https://instagram.com.evil.example/"))
        assertEquals(Destination.EXTERNAL_HTTPS, InstagramLoginUrlPolicy.classify("https://evilinstagram.com/"))
    }

    @Test
    fun insecureMalformedAndCredentialBearingUrlsAreBlocked() {
        assertEquals(Destination.BLOCK, InstagramLoginUrlPolicy.classify("http://instagram.com/"))
        assertEquals(Destination.BLOCK, InstagramLoginUrlPolicy.classify("javascript:alert(1)"))
        assertEquals(Destination.BLOCK, InstagramLoginUrlPolicy.classify("https://instagram.com:bad/"))
        assertEquals(Destination.BLOCK, InstagramLoginUrlPolicy.classify("https://user:pass@instagram.com/"))
    }
}
