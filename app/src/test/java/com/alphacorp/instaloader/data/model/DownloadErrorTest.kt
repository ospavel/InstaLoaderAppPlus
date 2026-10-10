package com.alphacorp.instaloader.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorTest {

    @Test
    fun instagramForbidden_isReportedWithHttpStatus() {
        val error = IllegalStateException(
            "ConnectionException: JSON Query to graphql/query: 403 Forbidden " +
                "when accessing https://www.instagram.com/graphql/query",
        )

        val mapped = error.toDownloadError()

        assertFalse(mapped is DownloadError.Network)
        assertTrue(mapped.userMessage.contains("HTTP 403"))
    }
}
