package com.alphacorp.instaloader.util

import java.net.URI
import java.util.Locale

object InstagramLoginUrlPolicy {

    enum class Destination {
        WEBVIEW,
        EXTERNAL_HTTPS,
        BLOCK,
    }

    fun classify(url: String): Destination {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return Destination.BLOCK
        }

        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            return Destination.BLOCK
        }
        if (uri.rawUserInfo != null) {
            return Destination.BLOCK
        }

        val host = uri.host.lowercase(Locale.ROOT)
        val approvedHost = APPROVED_DOMAINS.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
        val standardHttpsPort = uri.port == -1 || uri.port == 443
        return if (approvedHost && standardHttpsPort) {
            Destination.WEBVIEW
        } else {
            Destination.EXTERNAL_HTTPS
        }
    }

    private val APPROVED_DOMAINS = setOf("instagram.com", "facebook.com")
}
