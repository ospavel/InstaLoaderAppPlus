package com.alphacorp.instaloader.data.repository

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedMediaOrderingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun newestFirstUsesDownloadTimeInsteadOfMediaModificationTime() {
        val recentlyDownloaded = temporaryFolder.newFile("recent.jpg")
        val downloadedEarlier = temporaryFolder.newFile("earlier.jpg")
        recentlyDownloaded.setLastModified(1_600_000_000_000L)
        downloadedEarlier.setLastModified(1_700_000_000_000L)
        val downloadedAt = mapOf(
            recentlyDownloaded.absolutePath to 1_800_000_000_000L,
            downloadedEarlier.absolutePath to 1_750_000_000_000L,
        )

        val ordered = SavedMediaOrdering.newestFirst(
            listOf(downloadedEarlier, recentlyDownloaded),
            downloadedAt,
        )

        assertEquals(listOf(recentlyDownloaded, downloadedEarlier), ordered)
    }
}
