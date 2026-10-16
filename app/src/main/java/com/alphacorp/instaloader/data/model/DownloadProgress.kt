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
) {
    val isActive: Boolean
        get() = phase !in setOf("idle", "finished", "cancelled", "error")

    val progressFraction: Float?
        get() {
            if (transferFile.isNotBlank()) return transferProgressFraction
            transferProgressFraction?.let { return it }
            val totalCount = total ?: return null
            if (totalCount <= 0) return null
            return (completed + failed + skipped).toFloat() / totalCount.toFloat()
        }

    val transferProgressFraction: Float?
        get() {
            val totalBytes = transferTotalBytes ?: return null
            if (totalBytes <= 0L) return null
            val completedBytes = transferCompletedBytes ?: return null
            return (completedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }
}
