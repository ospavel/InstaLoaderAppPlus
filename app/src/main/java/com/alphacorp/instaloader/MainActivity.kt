package com.alphacorp.instaloader

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.alphacorp.instaloader.di.AppModule
import com.alphacorp.instaloader.service.DownloadForegroundService
import com.alphacorp.instaloader.ui.home.ClipboardReadEvent
import com.alphacorp.instaloader.ui.navigation.AppNavHost
import com.alphacorp.instaloader.ui.theme.InstaLoaderTheme
import com.alphacorp.instaloader.util.StorageAccess
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val downloadRepository by lazy { AppModule.downloadRepository(this) }
    private val sessionRepository by lazy { AppModule.sessionRepository(this) }
    private val parseInput by lazy { AppModule.parseInstagramInputUseCase() }

    private var pendingDownloadInput: String? = null
    private val clipboardReadEvents = MutableStateFlow(ClipboardReadEvent())
    private val clipboardManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
    }
    private var clipboardListenerRegistered = false
    private var clipboardReadPending = false
    private val clipboardChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        requestClipboardRead()
    }

    private val legacyStorageLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            continueDownloadPermissionFlow()
        } else {
            Toast.makeText(
                this,
                getString(R.string.storage_permission_required),
                Toast.LENGTH_LONG,
            ).show()
            pendingDownloadInput = null
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        startPendingDownload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        downloadRepository.sessionDirectory()
        downloadRepository.downloadBaseDir()

        setContent {
            InstaLoaderTheme {
                AppNavHost(
                    downloadRepository = downloadRepository,
                    sessionRepository = sessionRepository,
                    parseInput = parseInput,
                    clipboardReadEvents = clipboardReadEvents,
                    onStartDownload = ::startDownloadWithPermissions,
                    onCancelDownload = {
                        DownloadForegroundService.cancel(this)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerClipboardListener()
        requestClipboardRead()
        if (pendingDownloadInput != null && StorageAccess.hasWriteAccess(this)) {
            continueDownloadPermissionFlow()
        }
    }

    override fun onPause() {
        clipboardManager?.takeIf { clipboardListenerRegistered }
            ?.removePrimaryClipChangedListener(clipboardChangedListener)
        clipboardListenerRegistered = false
        clipboardReadPending = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clipboardReadPending) {
            window.decorView.post(::readClipboardIfFocused)
        }
    }

    private fun registerClipboardListener() {
        val manager = clipboardManager ?: return
        if (!clipboardListenerRegistered) {
            manager.addPrimaryClipChangedListener(clipboardChangedListener)
            clipboardListenerRegistered = true
        }
    }

    private fun requestClipboardRead() {
        clipboardReadPending = true
        window.decorView.post(::readClipboardIfFocused)
    }

    private fun readClipboardIfFocused() {
        if (!clipboardReadPending || !hasWindowFocus() || isFinishing || isDestroyed) return

        clipboardReadPending = false
        val clipboardText = runCatching {
            clipboardManager?.primaryClip
                ?.takeIf { clip -> clip.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
        }.getOrNull()

        clipboardReadEvents.value = ClipboardReadEvent(
            sequence = clipboardReadEvents.value.sequence + 1,
            text = clipboardText,
        )
    }

    private fun startDownloadWithPermissions(input: String) {
        pendingDownloadInput = input
        when {
            StorageAccess.needsLegacyWritePermission() &&
                !StorageAccess.hasWriteAccess(this) -> {
                legacyStorageLauncher.launch(StorageAccess.legacyWritePermission())
            }

            StorageAccess.needsManageStoragePermission() &&
                !StorageAccess.hasWriteAccess(this) -> {
                Toast.makeText(
                    this,
                    getString(R.string.storage_permission_required),
                    Toast.LENGTH_LONG,
                ).show()
                startActivity(StorageAccess.manageStorageSettingsIntent(this))
            }

            else -> continueDownloadPermissionFlow()
        }
    }

    private fun continueDownloadPermissionFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPendingDownload()
        }
    }

    private fun startPendingDownload() {
        pendingDownloadInput?.let { input ->
            DownloadForegroundService.start(this, input)
        }
        pendingDownloadInput = null
    }
}
