package com.alphacorp.instaloader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alphacorp.instaloader.data.model.DownloadLocationState
import com.alphacorp.instaloader.data.model.DownloadOptions
import com.alphacorp.instaloader.data.repository.DownloadRepository
import com.alphacorp.instaloader.util.StorageAccess
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _options = MutableStateFlow(DownloadOptions.Default)
    val options: StateFlow<DownloadOptions> = _options.asStateFlow()
    private var storedOptionsLoaded = false
    private var resetRequestedBeforeLoad = false
    private val pendingOptionTransforms = mutableListOf<(DownloadOptions) -> DownloadOptions>()

    val downloadLocation: StateFlow<DownloadLocationState> = downloadRepository.downloadDirectory
        .map(downloadRepository::downloadLocationState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DownloadLocationState(
                directory = File(""),
                summary = "",
                isDefault = true,
                selectedPreset = StorageAccess.LocationPreset.DOWNLOADS,
            ),
        )

    init {
        viewModelScope.launch {
            val storedOptions = downloadRepository.options.first()
            val shouldPersistPendingChanges = resetRequestedBeforeLoad || pendingOptionTransforms.isNotEmpty()
            var resolvedOptions = if (resetRequestedBeforeLoad) {
                DownloadOptions.Default
            } else {
                storedOptions
            }
            pendingOptionTransforms.forEach { transform ->
                resolvedOptions = transform(resolvedOptions)
            }
            pendingOptionTransforms.clear()
            resetRequestedBeforeLoad = false
            storedOptionsLoaded = true
            _options.value = resolvedOptions

            if (shouldPersistPendingChanges) {
                downloadRepository.saveOptions(resolvedOptions)
            }
        }
    }

    fun updateOptions(transform: (DownloadOptions) -> DownloadOptions) {
        val updatedOptions = transform(_options.value)
        _options.value = updatedOptions
        if (storedOptionsLoaded) {
            viewModelScope.launch {
                downloadRepository.saveOptions(updatedOptions)
            }
        } else {
            pendingOptionTransforms += transform
        }
    }

    fun resetOptions() {
        _options.value = DownloadOptions.Default
        if (storedOptionsLoaded) {
            viewModelScope.launch {
                downloadRepository.resetOptions()
            }
        } else {
            resetRequestedBeforeLoad = true
            pendingOptionTransforms.clear()
        }
    }

    fun selectPreset(preset: StorageAccess.LocationPreset) {
        viewModelScope.launch {
            downloadRepository.savePresetLocation(preset)
        }
    }

    fun setCustomFolderPath(path: String) {
        viewModelScope.launch {
            downloadRepository.saveDownloadLocation(path)
        }
    }

    fun resetDownloadLocation() {
        viewModelScope.launch {
            downloadRepository.resetDownloadLocation()
        }
    }

    class Factory(
        private val downloadRepository: DownloadRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(downloadRepository) as T
        }
    }
}
