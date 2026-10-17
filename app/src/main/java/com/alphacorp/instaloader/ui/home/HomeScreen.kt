package com.alphacorp.instaloader.ui.home

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.alphacorp.instaloader.ui.components.DownloadProgressCard
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    clipboardReadEvents: StateFlow<ClipboardReadEvent>,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardReadEvent by clipboardReadEvents.collectAsStateWithLifecycle()

    LaunchedEffect(clipboardReadEvent.sequence) {
        if (clipboardReadEvent.sequence > 0) {
            clipboardReadEvent.text?.let(viewModel::pasteInstagramUrlFromClipboard)
        }
    }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage, uiState.progress.phase) {
        val terminalPhase = uiState.progress.phase in setOf("finished", "error", "cancelled")
        if (!terminalPhase) {
            uiState.errorMessage?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessages()
            }
        } else if (uiState.successMessage != null) {
            viewModel.clearMessages()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            HomeHeroHeader(
                onOpenLogin = onOpenLogin,
                onOpenSettings = onOpenSettings,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HomeInputCard(
                    input = uiState.input,
                    targetLabel = uiState.targetLabel,
                    enabled = !uiState.isDownloading,
                    onInputChanged = viewModel::onInputChanged,
                    onPaste = {
                        val clipboardText = readClipboardText(context)
                        if (clipboardText.isNullOrEmpty()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(com.alphacorp.instaloader.R.string.clipboard_empty),
                                )
                            }
                        } else {
                            viewModel.pasteInputFromClipboard(clipboardText)
                        }
                    },
                )

                HomeDownloadButton(
                    enabled = !uiState.isDownloading,
                    onClick = viewModel::startDownload,
                )

                if (uiState.isDownloading) {
                    DownloadProgressCard(progress = uiState.progress)
                    HomeCancelButton(onClick = viewModel::cancelDownload)
                }

                if (uiState.progress.phase in setOf("finished", "error", "cancelled")) {
                    DownloadProgressCard(progress = uiState.progress)
                }
            }

            SavedFilesSection(
                files = uiState.savedFiles,
                refreshId = uiState.savedFilesRefreshId,
                onFileClick = { file ->
                    if (!openSavedFile(context, file)) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("No compatible app could open this file.")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun readClipboardText(context: Context): String? = runCatching {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboardManager?.primaryClip
        ?.takeIf { clip -> clip.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}.getOrNull()

private fun openSavedFile(context: Context, file: File): Boolean = try {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val mimeType = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
        ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: IllegalArgumentException) {
    false
} catch (_: SecurityException) {
    false
}
