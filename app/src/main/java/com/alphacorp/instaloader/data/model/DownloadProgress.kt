package com.alphacorp.instaloader.data.model

data class DownloadProgress(
    val phase: String = "idle",
    val total: Int? = null,
    val completed: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val remaining: Int? = null,
    val current: String = "",
    val message: String = "",
    val errorDetails: String = "",
    val transferFile: String = "",
    val transferCompletedBytes: Long? = null,
    val transferTotalBytes: Long? = null,
    val downloadedFilePath: String = "",
) {
    val isActive: Boolean
        get() = phase !in setOf("idle", "finished", "cancelled", "error")

    val progressFraction: Float?
        get() {
            val totalCount = total ?: return null
            if (totalCount <= 0) return null
            val inProgressItem = if (transferFile.isNotBlank()) {
                transferProgressFraction ?: 0f
            } else {
                0f
            }
            return ((completed + failed + skipped + inProgressItem) / totalCount.toFloat())
                .coerceIn(0f, 1f)
        }

    val transferProgressFraction: Float?
        get() {
            val totalBytes = transferTotalBytes ?: return null
            if (totalBytes <= 0L) return null
            val completedBytes = transferCompletedBytes ?: return null
            return (completedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }
}
