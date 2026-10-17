package com.alphacorp.instaloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alphacorp.instaloader.data.model.DownloadProgress

@Composable
fun DownloadProgressCard(
    progress: DownloadProgress,
    modifier: Modifier = Modifier,
) {
    when {
        progress.isActive -> DownloadProgressIndicator(progress, modifier)
        progress.phase in setOf("finished", "error", "cancelled") -> DownloadResultLine(progress, modifier)
    }
}

@Composable
private fun DownloadProgressIndicator(
    progress: DownloadProgress,
    modifier: Modifier,
) {
    val fraction = progress.progressFraction?.coerceIn(0f, 1f)
    val title = progress.transferFile.ifBlank {
        progress.current.ifBlank { progress.message }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title.ifBlank { "Downloading" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fraction != null) {
                Text(
                    text = "${(fraction * 100).toInt()}% total",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }

        if (progress.transferFile.isNotBlank()) {
            val transferred = progress.transferCompletedBytes ?: 0L
            val totalBytes = progress.transferTotalBytes
            val fileFraction = progress.transferProgressFraction
            Text(
                text = if (totalBytes != null && fileFraction != null) {
                    "File ${(fileFraction * 100).toInt()}% · ${formatByteCount(transferred)} / ${formatByteCount(totalBytes)}"
                } else {
                    "${formatByteCount(transferred)} downloaded"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fileFraction != null) {
                LinearProgressIndicator(
                    progress = { fileFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

@Composable
private fun DownloadResultLine(
    progress: DownloadProgress,
    modifier: Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var showErrorDetails by remember(progress.errorDetails) { mutableStateOf(false) }
    val isError = progress.phase == "error"
    val icon = when (progress.phase) {
        "finished" -> Icons.Default.CheckCircle
        "cancelled" -> Icons.Default.Close
        else -> Icons.Default.ErrorOutline
    }
    val iconColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val statusText = progress.message.ifBlank {
        when (progress.phase) {
            "finished" -> "Download finished"
            "cancelled" -> "Download cancelled"
            else -> "Download failed"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = statusText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isError && progress.errorDetails.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showErrorDetails = !showErrorDetails }) {
                    Text(if (showErrorDetails) "Hide details" else "Show details")
                }
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(progress.errorDetails))
                }) {
                    Text("Copy details")
                }
            }
            if (showErrorDetails) {
                SelectionContainer {
                    Text(
                        text = progress.errorDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatByteCount(byteCount: Long): String {
    if (byteCount < 1024L) return "$byteCount B"

    var value = byteCount.toDouble()
    val units = listOf("KB", "MB", "GB", "TB")
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return "%.1f %s".format(value, units[unitIndex])
}
