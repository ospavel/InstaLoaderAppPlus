package com.alphacorp.instaloader.data.repository

import java.io.File

internal object SavedMediaOrdering {
    fun newestFirst(files: List<File>, downloadedAt: Map<String, Long>): List<File> =
        files.sortedByDescending { file -> downloadedAt[file.absolutePath] ?: file.lastModified() }
}
