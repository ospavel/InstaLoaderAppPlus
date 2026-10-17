package com.alphacorp.instaloader.ui.home

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeFormattingTest {

    @Test
    fun formatsKilobytesAndMegabytesWithTheMatchingUnit() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("1.0 KB", FileSizeFormatter.format(1_024L))
            assertEquals("1.0 MB", FileSizeFormatter.format(1_048_576L))
            assertEquals("1.0 GB", FileSizeFormatter.format(1_073_741_824L))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
