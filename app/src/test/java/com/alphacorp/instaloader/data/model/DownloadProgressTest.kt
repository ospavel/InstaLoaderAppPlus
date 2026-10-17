package com.alphacorp.instaloader.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {

    @Test
    fun progressFractionCombinesCompletedItemsWithCurrentFileTransfer() {
        val progress = DownloadProgress(
            total = 4,
            completed = 1,
            transferFile = "media.jpg",
            transferCompletedBytes = 50L,
            transferTotalBytes = 100L,
        )

        assertEquals(0.375f, progress.progressFraction!!, 0.0001f)
    }

    @Test
    fun progressFractionDoesNotTreatAProfilePictureTransferAsTheWholeDownload() {
        val progress = DownloadProgress(
            phase = "transferring",
            transferFile = "profile.jpg",
            transferCompletedBytes = 100L,
            transferTotalBytes = 100L,
        )

        assertNull(progress.progressFraction)
    }
}
