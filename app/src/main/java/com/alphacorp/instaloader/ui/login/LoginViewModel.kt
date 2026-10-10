package com.alphacorp.instaloader.ui.login

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alphacorp.instaloader.data.repository.DownloadRepository
import com.alphacorp.instaloader.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

class LoginViewModel(
    private val sessionRepository: SessionRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                sessionRepository.currentSession(downloadRepository.sessionDirectory().absolutePath)
            }.onSuccess { session ->
                _uiState.value = _uiState.value.copy(
                    username = session?.username.orEmpty(),
                    isLoggedIn = session != null,
                    isLoading = false,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "The saved Instagram session could not be read. Sign in again.",
                )
            }
        }
    }

    fun importBrowserSession(cookieHeader: String, userAgent: String) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                sessionRepository.importBrowserSession(
                    cookieHeader = cookieHeader,
                    userAgent = userAgent,
                    sessionDir = downloadRepository.sessionDirectory().absolutePath,
                )
            }.onSuccess { session ->
                clearWebViewCookies {
                    _uiState.value = _uiState.value.copy(
                        username = session.username,
                        isLoggedIn = true,
                        isLoading = false,
                        successMessage = "Logged in as ${session.username}",
                    )
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = safeSessionError(error),
                )
            }
        }
    }

    fun logout() {
        sessionRepository.logout()
        clearWebViewCookies {
            _uiState.value = LoginUiState(isLoading = false)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun clearWebViewCookies(onCleared: () -> Unit) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            onCleared()
        }
    }

    private fun safeSessionError(error: Throwable): String {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return when {
            message.contains("connection", ignoreCase = true) ||
                message.contains("network", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ->
                "Instagram could not be reached. Check your connection and try again."

            message.startsWith("Instagram", ignoreCase = true) ||
                message.startsWith("The Instagram", ignoreCase = true) -> message

            else -> "Instagram could not confirm this login. Finish signing in and try again."
        }
    }

    class Factory(
        private val sessionRepository: SessionRepository,
        private val downloadRepository: DownloadRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(sessionRepository, downloadRepository) as T
        }
    }
}
